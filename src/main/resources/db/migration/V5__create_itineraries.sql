-- ──────────────────────────── itineraries ────────────────────────────
CREATE TABLE itineraries (
    id                 UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title              VARCHAR(100) NOT NULL,
    destination_region VARCHAR(100) NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL,
    party_size         INTEGER      NOT NULL DEFAULT 1,
    status             VARCHAR(20)  NOT NULL DEFAULT 'draft'
                           CHECK (status IN ('draft', 'confirmed', 'completed')),
    visibility         VARCHAR(20)  NOT NULL DEFAULT 'private'
                           CHECK (visibility IN ('public', 'private')),
    share_token        VARCHAR(64)  UNIQUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_itineraries_user_id ON itineraries(user_id);

-- ──────────────────────────── itinerary_items ────────────────────────────
CREATE TABLE itinerary_items (
    id                       UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    itinerary_id             UUID      NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    place_id                 UUID      NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    day_index                INTEGER,
    sort_order               INTEGER,
    planned_duration_minutes INTEGER,
    source_candidate_id      UUID      REFERENCES place_candidates(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_itinerary_items_itinerary ON itinerary_items(itinerary_id);
CREATE INDEX idx_itinerary_items_place ON itinerary_items(place_id);
