CREATE TABLE IF NOT EXISTS "match" (
  id UUID PRIMARY KEY,
  riot_match_id VARCHAR(255) NOT NULL,
  queue_id UUID NOT NULL,
  patch VARCHAR(64) NOT NULL,
  game_creation BIGINT NOT NULL,
  game_duration BIGINT NOT NULL,
  blue_team_side INTEGER,
  blue_team_kills INTEGER,
  blue_team_deaths INTEGER,
  blue_team_assists INTEGER,
  blue_team_healing INTEGER,
  red_team_side INTEGER,
  red_team_kills INTEGER,
  red_team_deaths INTEGER,
  red_team_assists INTEGER,
  red_team_healing INTEGER,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS player (
  id UUID PRIMARY KEY,
  puuid VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  tag VARCHAR(255) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uq_player_name_tag UNIQUE (name, tag)
);

CREATE TABLE IF NOT EXISTS player_match_stats (
  id UUID PRIMARY KEY,
  match_id UUID NOT NULL REFERENCES "match"(id) ON DELETE CASCADE,
  player_id UUID NOT NULL REFERENCES player(id) ON DELETE CASCADE,
  summoner_icon INTEGER NOT NULL,
  summoner_level INTEGER NOT NULL,
  kills INTEGER NOT NULL,
  deaths INTEGER NOT NULL,
  assists INTEGER NOT NULL,
  healing INTEGER NOT NULL,
  damage_to_players INTEGER NOT NULL,
  wards INTEGER NOT NULL,
  gold INTEGER NOT NULL,
  role INTEGER NOT NULL,
  champion VARCHAR(64) NOT NULL,
  cs_per_minute DOUBLE PRECISION NOT NULL,
  killed_minions INTEGER NOT NULL,
  triple_kills INTEGER NOT NULL,
  quad_kills INTEGER NOT NULL,
  penta_kills INTEGER NOT NULL,
  side INTEGER NOT NULL,
  won BOOLEAN NOT NULL,
  mmr_delta INTEGER,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS training_sync_outbox (
  id UUID PRIMARY KEY,
  riot_match_id VARCHAR(255) NOT NULL,
  status VARCHAR(64) NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  next_attempt_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
