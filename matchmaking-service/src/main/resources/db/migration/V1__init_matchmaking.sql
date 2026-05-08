CREATE TABLE IF NOT EXISTS queue (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  region VARCHAR(64),
  namespace VARCHAR(255),
  required_players INTEGER NOT NULL DEFAULT 10,
  is_role_queue BOOLEAN NOT NULL DEFAULT FALSE,
  mode VARCHAR(64) NOT NULL,
  mmr_window INTEGER NOT NULL DEFAULT 200
);

CREATE TABLE IF NOT EXISTS player (
  id UUID PRIMARY KEY,
  discord_username VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS lobby (
  id UUID PRIMARY KEY,
  queue_id UUID NOT NULL REFERENCES queue(id),
  region VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS "match" (
  id UUID PRIMARY KEY,
  lobby_id UUID NOT NULL REFERENCES lobby(id),
  state VARCHAR(64) NOT NULL,
  external_game_id VARCHAR(255),
  started_at TIMESTAMP,
  ended_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS match_ticket (
  id UUID PRIMARY KEY,
  player_id UUID NOT NULL REFERENCES player(id),
  queue_id UUID NOT NULL REFERENCES queue(id),
  region VARCHAR(64) NOT NULL,
  role VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  mmr INTEGER NOT NULL,
  riot_puuid VARCHAR(255),
  team INTEGER,
  created_at TIMESTAMP NOT NULL,
  lobby_id UUID REFERENCES lobby(id)
);

CREATE TABLE IF NOT EXISTS match_acceptances (
  match_id UUID NOT NULL REFERENCES "match"(id) ON DELETE CASCADE,
  player_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS match_result_outbox (
  id UUID PRIMARY KEY,
  match_id UUID NOT NULL UNIQUE,
  external_game_id VARCHAR(255) NOT NULL,
  queue_id UUID NOT NULL,
  player_deltas_json TEXT NOT NULL,
  status VARCHAR(64) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP,
  last_error VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS match_lifecycle_events (
  id UUID PRIMARY KEY,
  match_id UUID NOT NULL,
  step VARCHAR(128) NOT NULL,
  status VARCHAR(64) NOT NULL,
  message VARCHAR(1000),
  created_at TIMESTAMP NOT NULL
);
