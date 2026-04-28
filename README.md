# RoundTrip-Backend

## Tech Stack

| 구분          | 기술                               | 버전                          |
| ------------- | ---------------------------------- | ----------------------------- |
| Language      | Java 21 (LTS)                      | `21`                          |
| Framework     | Spring Boot                        | `4.0.6`                       |
| Security      | Spring Security + JWT              | `7.0.5`                       |
| Persistence   | Spring Data JPA · Hibernate ORM    | `7.3`                         |
| Migration     | Flyway                             | `12.5.0`                      |
| Async / Cache | Redisson · Spring `@Async`         | `4.3.1`                       |
| Realtime      | Spring WebSocket (STOMP)           |                               |
| Push          | Firebase Admin SDK (FCM + APNs)    | `9.8.0`                       |
| Docs          | SpringDoc OpenAPI                  | `3.0.3`                       |
| Build         | Gradle (Kotlin DSL)                | `9.4.1`                       |
| Test          | JUnit 5 · Mockito · Testcontainers | `5.14.4` · `5.22.0` · `2.0.5` |

---

## Conventions

- 브랜치 전략 및 커밋 규칙 → [CONTRIBUTING.md](CONTRIBUTING.md)
- 이슈 / PR 작성 → [.github/](.github/)
