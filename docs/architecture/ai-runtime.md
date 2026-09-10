# AI 런타임

사용자 요청이 백엔드와 Redis 세마포어를 지나 FeatherlessAI로 가는 경로만 적는다. 이 저장소는 Kubernetes나 서비스 메시를 쓰지 않는다.

## 두 갈래

대화형 Planning Agent와 백그라운드 장소 추출이 같은 Redis 세마포어 `featherlessai:concurrency`를 쓴다. 최대 permit은 4다. Premium 플랜의 동시 접속 한도에 맞춘 값이다. 구현은 `FeatherlessAiRateLimiter`다.

세마포어를 잡는 순서는 `FeatherlessAiIsolation`이 정한다. 서킷이 열려 있으면 permit을 잡지 않는다. 서킷이 닫혀 있으면 permit을 잡고 Provider를 호출한다. 예외가 나도 `finally`에서 permit을 반환한다. 재시도 동안에는 잡은 permit을 유지한다.

## 대화형 Agent

1. 인증된 사용자가 일정 채팅 API를 호출한다.
2. `PlanningAgentService`가 일정 맥락과 도구 정의를 붙여 FeatherlessAI `/chat/completions`를 호출한다.
3. `isolation.run("planning_chat", 10초)`로 서킷을 보고 세마포어를 잡는다.
4. 잡은 뒤에만 `invokeHttp("planning_chat")`가 HTTP를 보낸다.
5. 서킷이 열려 있으면 "지금은 외부 AI를 사용할 수 없습니다. 잠시 후 다시 시도해주세요."를 돌려준다. permit을 잡지 못한 경우에는 "현재 다른 요청을 처리 중입니다. 잠시 후 다시 시도해주세요."를 돌려준다. 가짜 일정을 성공처럼 만들지 않는다.

## 백그라운드 추출

1. 사용자가 소스 링크를 제출하면 추출 잡이 생긴다.
2. `ExtractionPipelineService`가 먼저 Supadata로 메타데이터와 자막을 가져온다. 이 단계는 FeatherlessAI 세마포어 밖이다.
3. `FeatherlessAiClient.parsePlaces`가 `isolation.run("place_extraction")`으로 세마포어를 잡는다. 획득 타임아웃은 설정값 `featherlessai.acquire-timeout`이고 기본 60초다.
4. FeatherlessAI가 장소 JSON을 주면 Kakao Local로 좌표를 맞춘다. Kakao 호출은 이 세마포어를 쓰지 않는다.
5. Provider 호출이 실패하면 빈 장소 목록으로 폴백한다. 없는 장소를 지어내지 않는다.

## 공유하는 이유

추출 잡과 Agent가 각자 한도를 두면 FeatherlessAI 동시 접속 4를 쉽게 넘는다. 한 Redis 키로 두 경로를 묶어서 Provider 앞단에서 막는다.
