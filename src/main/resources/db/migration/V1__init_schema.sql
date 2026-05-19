CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

-- ──────────────────────────── users ────────────────────────────
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

-- ──────────────────────────── source_links ────────────────────────────
-- enum 값은 VARCHAR + CHECK 제약으로 관리 (Hibernate @Enumerated(EnumType.STRING) 호환)
CREATE TABLE source_links (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type         VARCHAR(30)  CHECK (source_type IN ('YOUTUBE_SHORT', 'INSTAGRAM_REEL')),
    url                 TEXT         NOT NULL,
    normalized_url_hash VARCHAR(64)  NOT NULL,
    title               VARCHAR(500),
    thumbnail_url       VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    visibility          VARCHAR(20)  NOT NULL DEFAULT 'private',
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_source_links_user_hash ON source_links(user_id, normalized_url_hash);
CREATE INDEX idx_source_links_user_id ON source_links(user_id);

-- ──────────────────────────── extraction_jobs ────────────────────────────
CREATE TABLE extraction_jobs (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_link_id  UUID         NOT NULL REFERENCES source_links(id) ON DELETE CASCADE,
    job_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (job_status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    signal_count    INTEGER      NOT NULL DEFAULT 0,
    error_code      VARCHAR(100),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_extraction_jobs_source_link ON extraction_jobs(source_link_id);

-- ──────────────────────────── places ────────────────────────────
CREATE TABLE places (
    id               UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    canonical_name   VARCHAR(255)  NOT NULL,
    latitude         NUMERIC(9,6),
    longitude        NUMERIC(9,6),
    geom             geography(Point, 4326),
    category         VARCHAR(30)   CHECK (category IN ('ATTRACTION','RESTAURANT','CAFE','ACCOMMODATION','NATURE','ETC')),
    country_code     VARCHAR(10),
    google_place_id  VARCHAR(255),
    kakao_place_id   VARCHAR(255),
    thumbnail_url    VARCHAR(500),
    thumbnail_source VARCHAR(20)   CHECK (thumbnail_source IN ('FLICKR','WIKIMEDIA','GOOGLE_PLACES')),
    evidence         TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ──────────────────────────── place_candidates ────────────────────────────
CREATE TABLE place_candidates (
    id                    UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id                UUID           NOT NULL REFERENCES extraction_jobs(id) ON DELETE CASCADE,
    place_id              UUID           REFERENCES places(id),
    candidate_name        VARCHAR(255)   NOT NULL,
    category              VARCHAR(100),
    confidence_score      NUMERIC(5,4)   NOT NULL DEFAULT 0,
    rank_order            INTEGER        NOT NULL DEFAULT 0,
    provider_match_json   JSONB,
    requires_confirmation BOOLEAN        NOT NULL DEFAULT false,
    status                VARCHAR(20)    NOT NULL DEFAULT 'PROPOSED'
                              CHECK (status IN ('PROPOSED','ACCEPTED','REJECTED','EDITED')),
    evidence              TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_place_candidates_job ON place_candidates(job_id);
