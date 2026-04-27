CREATE TABLE players (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_score      BIGINT       NOT NULL DEFAULT 0,
    last_played_at  TIMESTAMPTZ
);

CREATE INDEX idx_players_username ON players (username);
