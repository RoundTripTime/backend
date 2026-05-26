CREATE TABLE ad_sessions (
                             id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
                             user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                             expires_at    TIMESTAMPTZ NOT NULL,
                             is_completed  BOOLEAN     NOT NULL DEFAULT false,
                             created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ad_sessions_user_completed_created
    ON ad_sessions(user_id, is_completed, created_at DESC);
