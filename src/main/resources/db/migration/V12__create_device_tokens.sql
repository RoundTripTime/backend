-- ──────────────────────────── device_tokens ────────────────────────────
-- FCM 푸시 전송 대상이 되는 사용자별 디바이스 토큰
CREATE TABLE device_tokens (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL UNIQUE,
    platform   VARCHAR(20) NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
