CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    email          VARCHAR(255),
    nickname       VARCHAR(50)  NOT NULL,
    avatar_url     VARCHAR(500),
    locale         VARCHAR(20)  NOT NULL DEFAULT 'ko-KR',
    home_region    VARCHAR(100) NOT NULL,
    map_provider   VARCHAR(20)  NOT NULL DEFAULT 'kakao',
    credit_balance INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE user_social_accounts (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL,
    social_id  VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_social_provider_id UNIQUE (provider, social_id)
);

CREATE INDEX idx_social_accounts_user_id ON user_social_accounts (user_id);

CREATE TABLE user_follows (
    follower_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    following_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id)
);

CREATE INDEX idx_user_follows_following ON user_follows (following_id);
