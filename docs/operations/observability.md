# 관측

## 왜 모으는가

외부 AI 한도와 지연이 사용자 지연으로 이어진다. 요청이 실패했는지, 세마포어에서 기다렸는지, Provider가 느린지를 나누지 않으면 재시도나 permit 수를 잘못 건드리게 된다.

## 수집 경로

Spring Actuator는 `health`와 `prometheus`만 연다. `GET /actuator/prometheus`는 인증 없이 읽을 수 있다. 메트릭 라벨에는 userId, prompt, requestId를 넣지 않는다.

배포 후 합성 확인은 `GET /actuator/health`와 `GET /actuator/health/smoke`다. smoke 그룹은 `db`와 `redis`가 `UP`인 JSON만 본다. 유료 외부 API는 부르지 않는다.

## Provider 메트릭

`AiProviderMetrics`가 기록한다. Prometheus 이름은 점(.)이 밑줄로 바뀐다.

| 메트릭 | 의미 |
| --- | --- |
| `ai_provider_requests_total` | 호출 횟수. `provider`, `operation`, `result` 라벨 |
| `ai_provider_request_duration_seconds` | Provider HTTP 지연 |
| `ai_concurrency_available` | 남은 permit |
| `ai_concurrency_used` | `4 - available` |
| `ai_concurrency_wait_duration_seconds` | 세마포어 대기. `result`는 `acquired`, `timeout`, `interrupted` |
| `ai_concurrency_acquire_failures_total` | 획득 실패 |

`result`는 `success`, `timeout`, `rate_limited`, `client_error`, `server_error`, `circuit_open`, `fallback`, `unknown_error`만 쓴다. `operation`은 추출이 `place_extraction`, Agent가 `planning_chat`이다. `provider`는 FeatherlessAI 경로에서 `featherless`다.

파이프라인 지연은 `extraction_pipeline_duration_seconds`, `extraction_pipeline_stage_duration_seconds`, `planning_agent_duration_seconds`, `planning_agent_requests_total`이다.

## 대시보드와 알림

대시보드 JSON은 `observability/grafana/dashboards/roundtrip-ai-operations.json`이다. 패널은 요청 속도, 오류 비율, Provider p50/p95/p99, 세마포어 used/available, 대기 p95, 추출 p95, Planning Agent p95다.

알림 규칙은 `observability/prometheus/rules/ai-operations.yml`이다. 규칙은 아래 세 개다.

| 알림 | 조건 | 유지 시간 |
| --- | --- | --- |
| `AIProviderHighErrorRate` | 오류 result 비율 > 10% | 2분 |
| `AIProviderHighLatency` | Provider p95 > 60초 | 5분 |
| `AIConcurrencySaturation` | 남은 permit이 0 | 1분 |

오류 비율의 result는 `timeout`, `rate_limited`, `client_error`, `server_error`, `circuit_open`, `unknown_error`다. `success`와 `fallback`은 오류로 세지 않는다.

p95 60초는 목 부하 실험의 사용자 지연 8초를 운영 LLM 지연으로 쓰지 않기 때문이다. 실험 해석은 [AI 동시성 부하 실험](../performance/ai-load-test.md)에 있다.

## 장애 때 먼저 볼 것

1. `ai_concurrency_available`이 0으로 붙어 있는지 본다. 0이면 세마포어 포화다. 사용자 지연은 커지고 Provider p95는 그대로일 수 있다.
2. `ai_provider_requests_total`의 `result`를 본다. `timeout`과 `server_error`가 늘면 Provider 쪽이다. `circuit_open`이면 서킷이 이미 열린 것이다. `rate_limited`면 429다. `fallback`이면 permit을 못 잡고 HTTP를 안 보낸 것이다.
3. Provider p95와 세마포어 대기 p95를 나란히 본다. 대기만 커지면 permit 경합이다. Provider p95가 60초에 가까우면 호출이 사실상 멈춘 쪽에 가깝다.
4. 추출과 Agent 파이프라인 p95를 본다. Agent만 이상하면 채팅 경로의 획득 타임아웃 10초를 의심한다.
