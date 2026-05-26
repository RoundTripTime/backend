-- ──────────────────────────── community_posts ────────────────────────────
CREATE TABLE community_posts (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body          TEXT         NOT NULL,
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'public'
                      CHECK (visibility IN ('public', 'followers')),
    like_count    INTEGER      NOT NULL DEFAULT 0,
    comment_count INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_community_posts_user_id ON community_posts(user_id);
CREATE INDEX idx_community_posts_created_at ON community_posts(created_at DESC);

-- ──────────────────────────── post_tagged_places ────────────────────────────
CREATE TABLE post_tagged_places (
    post_id  UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, place_id)
);

-- ──────────────────────────── post_tagged_itineraries ────────────────────────────
CREATE TABLE post_tagged_itineraries (
    post_id      UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    itinerary_id UUID NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, itinerary_id)
);

-- ──────────────────────────── post_likes ────────────────────────────
CREATE TABLE post_likes (
    post_id    UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

-- ──────────────────────────── post_comments ────────────────────────────
CREATE TABLE post_comments (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id    UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_comments_post_id ON post_comments(post_id);
