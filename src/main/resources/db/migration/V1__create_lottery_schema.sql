CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    max_draws_per_user INTEGER NOT NULL CHECK (max_draws_per_user > 0),
    no_win_probability_basis_points INTEGER NOT NULL
        CHECK (no_win_probability_basis_points BETWEEN 0 AND 10000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE campaign_prizes (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    stock_remaining INTEGER NOT NULL CHECK (stock_remaining >= 0),
    probability_basis_points INTEGER NOT NULL
        CHECK (probability_basis_points BETWEEN 0 AND 10000),
    UNIQUE (campaign_id, name)
);

CREATE INDEX idx_campaign_prizes_campaign_id ON campaign_prizes(campaign_id);

CREATE TABLE user_campaign_stats (
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    user_id VARCHAR(200) NOT NULL,
    draws_used INTEGER NOT NULL CHECK (draws_used >= 0),
    PRIMARY KEY (campaign_id, user_id)
);

CREATE TABLE draw_requests (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
    user_id VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    requested_count INTEGER NOT NULL CHECK (requested_count > 0),
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (campaign_id, user_id, idempotency_key)
);

CREATE INDEX idx_draw_requests_campaign_user ON draw_requests(campaign_id, user_id);

CREATE TABLE draw_history (
    id BIGSERIAL PRIMARY KEY,
    draw_request_id BIGINT NOT NULL REFERENCES draw_requests(id) ON DELETE CASCADE,
    draw_number INTEGER NOT NULL CHECK (draw_number > 0),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('WON', 'NO_PRIZE', 'SOLD_OUT')),
    prize_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (draw_request_id, draw_number)
);
