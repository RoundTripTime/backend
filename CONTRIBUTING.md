# Contributing Guide

## 브랜치 전략

```
main          ← 배포 브랜치 (직접 push 금지)
develop       ← 통합 브랜치 (PR 대상)
│
├── feat/...      새 기능
├── fix/...       버그 수정
├── refactor/...  리팩토링 (동작 변경 없음)
├── chore/...     빌드·설정·의존성
├── docs/...      문서
└── hotfix/...    긴급 배포 수정 (main에서 분기)
```

### 브랜치 이름 규칙

```
<type>/<issue-number>-<short-description>

예) feat/42-jwt-refresh-token
    fix/78-user-not-found-500
    chore/91-upgrade-spring-boot-3.4
```

---

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 규칙을 따릅니다.

```
<type>(<scope>): <subject>

[optional body]

[optional footer — e.g. Closes #42]
```

### Type

| 타입       | 설명                  |
| ---------- | --------------------- |
| `feat`     | 새 기능               |
| `fix`      | 버그 수정             |
| `refactor` | 리팩토링              |
| `test`     | 테스트 추가·수정      |
| `docs`     | 문서 변경             |
| `chore`    | 빌드·설정·의존성·기타 |

### 예시

```
feat(auth): JWT refresh token 발급 로직 추가

- RTR(Refresh Token Rotation) 방식 적용
- Redis에 refresh token 저장 (TTL 14d)

Closes #42
```

---

## Pull Request

1. `develop` 브랜치를 최신 상태로 유지한 뒤 작업 브랜치를 생성하세요.
2. PR은 **하나의 목적**만 담습니다. 리뷰어가 맥락을 파악하기 쉽게 작게 나눠 주세요.
3. PR 제목도 커밋 메시지 규칙과 동일하게 작성합니다.
4. PR 템플릿의 모든 섹션을 채우고, 불필요한 섹션은 삭제해 주세요.
5. CI가 통과해야 리뷰를 요청할 수 있습니다.
6. Approve 1명 이상이면 본인이 직접 머지합니다.

---

## 코드 리뷰

- 리뷰 코멘트에 **prefix**를 붙여 의도를 명확히 합니다.

| prefix            | 의미                                |
| ----------------- | ----------------------------------- |
| `p:` (praise)     | 칭찬, 긍정 피드백                   |
| `q:` (question)   | 궁금한 점, 이해를 위한 질문         |
| `s:` (suggestion) | 개선 제안 (반영 여부는 작성자 판단) |
| `n:` (nit)        | 사소한 지적 (머지 블로커 아님)      |
| `r:` (required)   | 반드시 수정 필요                    |

---

### Flyway 마이그레이션 파일 이름 규칙

```
V{version}__{description}.sql

예) V1__init_schema.sql
    V2__add_user_refresh_token.sql
```

- 버전은 단조 증가여야 합니다.
- 한 번 적용된 파일은 절대 수정하지 마세요. 수정이 필요하면 새 버전 파일을 만드세요.

### API 문서

- 애플리케이션 실행 후 http://localhost:8080/swagger-ui.html 에서 확인합니다.
- `@Operation`, `@Parameter` 등 SpringDoc 어노테이션으로 문서를 유지합니다.
