# 실패 처리

FeatherlessAI 격리만 이 문서의 대상이다. 타임아웃과 재시도, 서킷, 폴백은 `application.yml`의 `featherlessai`와 `FeatherlessAiFailurePolicy`, `FeatherlessAiIsolation`이 정한다.

## 호출 순서

1. 서킷이 `OPEN` 또는 `FORCED_OPEN`이면 세마포어를 잡지 않고 폴백한다. 메트릭 `result`는 `circuit_open`이다.
2. 세마포어 획득에 실패하면 HTTP를 보내지 않는다. permit도 반환하지 않는다. 메트릭 `result`는 `fallback`이다.
3. permit을 잡은 뒤에 HTTP를 보낸다. Resilience4j Retry가 CircuitBreaker와 `recordCall`을 감싼다.
4. 어떤 예외가 나도 permit은 반환한다.

## 타임아웃

연결 타임아웃 기본값은 2초, 읽기 타임아웃 기본값은 30초다. 값은 `featherlessai.connect-timeout`, `featherlessai.read-timeout`이다. 코드에 초 숫자를 반복해 넣지 않는다.

세마포어 획득 타임아웃은 경로마다 다르다. 추출은 `featherlessai.acquire-timeout`이고 기본 60초다. Planning Agent는 10초로 고정이다.

## 재시도

최대 3회, 대기 200ms, 배수 2.0, 타임아웃 재시도는 켠 상태가 기본이다. 재시도하는 실패는 5xx, 연결 실패, 설정으로 켠 타임아웃이다. 400, 401, 403, 검증 오류, 429, 서킷 거부(`CallNotPermittedException`)는 재시도하지 않는다.

## 429

429는 5xx 재시도와 같은 줄에 두지 않는다. `Retry-After` 초 값을 읽는다. `featherlessai.rate-limit.honor-retry-after`는 true다. `max-wait-for-retry-after` 기본값은 0초라서 호출 스레드를 재우지 않는다. permit을 붙잡은 채 120초를 기다리지 않기 위해서다. 메트릭 `result`는 `rate_limited`다.

## 서킷 브레이커

Resilience4j를 쓴다. 기본값은 슬라이딩 윈도우 10, 최소 호출 5, 실패율 50%, OPEN 유지 30초, half-open에서 2회다. 서킷에 넣는 실패는 5xx, 429, 타임아웃, 연결 실패다. 400과 403은 넣지 않는다.

## 폴백

가짜 성공을 정상 결과처럼 주지 않는다.

| 경로 | 서킷 열림 | 세마포어 획득 실패 | HTTP 실패 후 |
| --- | --- | --- | --- |
| 장소 추출 | 빈 목록 | 빈 목록 | 빈 목록 |
| Planning Agent | 지금은 외부 AI를 사용할 수 없습니다. 잠시 후 다시 시도해주세요. | 현재 다른 요청을 처리 중입니다. 잠시 후 다시 시도해주세요. | 죄송합니다. 요청 처리 중 오류가 발생했습니다. |

## Provider별 정책

| Provider | 이 격리 적용 | 비고 |
| --- | --- | --- |
| FeatherlessAI | 예. 타임아웃, 재시도, 서킷, Redis 세마포어 4 | 추출과 Agent가 공유한다 |
| Supadata | 아니오 | 추출 앞단에서 메타데이터와 자막을 가져온다. FeatherlessAI 세마포어 밖이다 |
| Kakao Local | 아니오 | 추출 뒤 좌표 검색에 쓴다. 세마포어 밖이다 |
| Google Places, Naver, MyRealTrip | 아니오 | 이 문서의 서킷과 세마포어를 타지 않는다 |

다른 Provider에 같은 격리를 넣는 일은 별개의 이슈로 처리한다.
