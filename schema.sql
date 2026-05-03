CREATE TABLE IF NOT EXISTS queue (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(64),
    namespace VARCHAR(255),
    required_players INT NOT NULL DEFAULT 10,
    is_role_queue BOOLEAN NOT NULL DEFAULT FALSE,
    mode VARCHAR(64) NOT NULL,
    mmr_window INT NOT NULL DEFAULT 200
);
