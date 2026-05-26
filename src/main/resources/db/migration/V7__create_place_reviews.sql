-- ──────────────────────────── place_reviews ────────────────────────────
CREATE TABLE place_reviews (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    place_id   UUID        NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_place_reviews_place_user UNIQUE (place_id, user_id)
);

CREATE INDEX idx_place_reviews_place_id ON place_reviews(place_id);
