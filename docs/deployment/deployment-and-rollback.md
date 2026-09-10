# 배포와 롤백

이 문서는 `scripts/deploy.sh`와 `.github/workflows/deploy.yml`과 같아야 한다. Kubernetes나 Helm으로 배포하지 않는다. GitHub Actions가 JAR을 EC2로 보내고 systemd 유닛 `roundtrip`을 재시작한다.

## 워크플로

트리거는 `main` 푸시와 `workflow_dispatch`다. 워크플로 파일은 `.github/workflows/deploy.yml` 하나다.

1. **Build.** Java 21, 테스트용 Postgres 이미지, `./gradlew clean build -x test`.
2. **Test.** `./gradlew test`. 기본 테스트 태스크는 `@Tag("external")`를 제외하므로 실제 외부 Provider 테스트는 이 단계에서 돌지 않는다.
3. **Package.** `build/libs/roundtrip.jar`를 아티팩트로 올린다. 보관 3일이다.
4. **Deploy.** 아티팩트와 `scripts/deploy.sh`, `scripts/roundtrip.service`를 EC2로 복사한다. JAR는 `/opt/roundtrip/roundtrip-new.jar`다. GitHub Secrets로 `/opt/roundtrip/.env`와 Firebase 키 파일을 만든다. 시크릿 값은 이 문서에 적지 않는다. 이름 목록은 워크플로 파일을 본다.
5. **Verify.** `sudo bash /opt/roundtrip/deploy.sh`가 health와 스모크를 수행한다. 실패하면 배포 job이 실패한다.

## deploy.sh 순서

작업 디렉터리는 `/opt/roundtrip`이다. 서비스 이름은 `roundtrip`이다.

1. 현재 `roundtrip.jar`가 있으면 `roundtrip-old.jar`로 복사한다. 첫 배포면 백업하지 않는다.
2. `roundtrip-new.jar`를 `roundtrip.jar`로 교체한다.
3. `systemctl restart roundtrip`을 실행한다. 기본은 `sudo`를 붙인다.
4. `http://localhost:8080/actuator/health`를 최대 30번, 2초 간격으로 확인한다.
5. health가 통과하면 `http://localhost:8080/actuator/health/smoke`를 한 번 호출한다. HTTP가 200이고 JSON `status`가 `UP`이며 `components.db`와 `components.redis`가 `UP`이어야 한다. 응답 본문은 로그에 남기지 않는다.

health 또는 스모크가 실패하면 배포 실패다. `roundtrip-old.jar`가 있으면 다시 `roundtrip.jar`로 되돌리고 서비스를 재시작한 뒤 health를 같은 방식으로 다시 확인한다. 이전 JAR가 없으면 롤백하지 않고 실패한다. 롤백 health가 통과해도 배포 자체는 실패로 끝난다.

## 스모크가 보지 않는 것

스모크는 FeatherlessAI, Supadata, Kakao, Google Places를 호출하지 않는다. DB와 Redis 연결만 본다.

## 로컬에서 스크립트만 검증

`DeployScriptTest`가 목 HTTP 서버로 health 실패, 스모크 실패, 성공 경로를 실행한다. EC2에 붙지 않는다.
