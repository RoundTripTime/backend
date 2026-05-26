-- ──────────────────────────── market_plans ────────────────────────────
CREATE TABLE market_plans (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    itinerary_id   UUID        NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          VARCHAR(50) NOT NULL,
    description    TEXT        NOT NULL,
    highlight      VARCHAR(100) NOT NULL,
    pros           TEXT        NOT NULL,
    cons           TEXT        NOT NULL,
    tips           TEXT,
    credit_price   INTEGER     NOT NULL DEFAULT 1,
    view_count     INTEGER     NOT NULL DEFAULT 0,
    is_listed      BOOLEAN     NOT NULL DEFAULT true,
    ota_booked_at  DATE,
    ota_verified   BOOLEAN     NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_market_plans_itinerary UNIQUE (itinerary_id)
);

CREATE INDEX idx_market_plans_user_id ON market_plans(user_id);
CREATE INDEX idx_market_plans_listed ON market_plans(is_listed, created_at DESC);

-- ──────────────────────────── credit_histories ────────────────────────────
CREATE TABLE credit_histories (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credit_type   VARCHAR(30) NOT NULL
                      CHECK (credit_type IN ('ad_view', 'ota_booking', 'plan_sale', 'plan_purchase', 'ota_payment')),
    amount        INTEGER     NOT NULL,
    balance_after INTEGER     NOT NULL,
    description   VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_histories_user_id ON credit_histories(user_id);
