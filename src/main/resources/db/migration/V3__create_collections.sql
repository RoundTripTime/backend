CREATE TABLE collections (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    icon        VARCHAR(10),
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    visibility  VARCHAR(10)  NOT NULL DEFAULT 'private'
                    CHECK (visibility IN ('public', 'private')),
    share_token VARCHAR(64)  UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_collections_user_id ON collections(user_id);

CREATE TABLE collection_places (
    collection_id UUID        NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    place_id      UUID        NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (collection_id, place_id)
);

CREATE INDEX idx_collection_places_collection ON collection_places(collection_id);
