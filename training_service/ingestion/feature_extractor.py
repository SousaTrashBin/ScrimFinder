"""
training_service/ingestion/feature_extractor.py

Converts raw Riot API v5 match JSON into feature vectors for each ML concern.

Raw Riot JSON structure (relevant fields):
  metadata.matchId
  info.gameVersion
  info.queueId
  info.gameDuration
  info.participants[*]:
    puuid, riotIdGameName, riotIdTagline
    championName, teamId, teamPosition, win
    kills, deaths, assists
    goldEarned, totalMinionsKilled
    totalDamageDealt, wardsPlaced
    item0..item6
    perks.styles[*].selections[*].perk

Feature vectors produced:
  draft:       {blue_champ_ids: [5], red_champ_ids: [5], winner: "blue"|"red"}
  build:       per player → {champion, position, item_ids, rune_ids, gold, cs, dmg, win}
  performance: per player → {kills, deaths, assists, gold, cs, dmg, vision, kda, kp,
                              position, champion, win, duration}
"""

from __future__ import annotations

import json
from typing import Any, Optional

# ── Position mapping ──────────────────────────────────────────
# Riot API teamPosition values → our DB position values
_POSITION_MAP = {
    "TOP": "TOP",
    "JUNGLE": "JUNGLE",
    "MIDDLE": "MIDDLE",
    "BOTTOM": "BOTTOM",
    "UTILITY": "UTILITY",
    # Fallbacks for older API responses
    "MID": "MIDDLE",
    "BOT": "BOTTOM",
    "SUPPORT": "UTILITY",
    "ADC": "BOTTOM",
    "": "MIDDLE",  # unknown → treat as mid
}


def _normalise_position(raw: str) -> str:
    return _POSITION_MAP.get(raw.upper(), "MIDDLE")


def _safe_kda(kills: int, deaths: int, assists: int) -> float:
    return round((kills + assists) / max(deaths, 1), 2)


def _safe_kp(kills: int, assists: int, team_kills: int) -> float:
    if team_kills == 0:
        return 0.0
    return round((kills + assists) / team_kills, 4)


def _extract_items(participant: dict) -> list[int]:
    """Extract non-zero item IDs from item0..item6."""
    items = []
    for slot in range(7):
        item_id = participant.get(f"item{slot}", 0)
        if item_id and item_id != 0:
            items.append(item_id)
    return items


def _extract_runes(participant: dict) -> list[int]:
    """
    Extract rune IDs from perks.styles[*].selections[*].perk
    Falls back to empty list if perks not present.
    """
    runes = []
    try:
        perks = participant.get("perks", {})
        styles = perks.get("styles", [])
        for style in styles:
            for selection in style.get("selections", []):
                perk_id = selection.get("perk", 0)
                if perk_id:
                    runes.append(perk_id)
    except Exception:
        pass
    return runes


# ── Main extraction function ──────────────────────────────────


def extract(raw: dict | str) -> dict:
    """
    Extract feature vectors from a raw Riot API v5 match JSON.

    Args:
        raw: dict or JSON string of the Riot match response

    Returns:
        {
          "match_id":    str,
          "patch":       str,
          "queue_id":    int,
          "duration":    int,   # seconds
          "draft":       DraftFeatures | None,
          "build":       list[BuildFeatures],      # one per player
          "performance": list[PerformanceFeatures], # one per player
        }

    Each sub-dict has a "valid" key — False if data was incomplete.
    """
    if isinstance(raw, str):
        raw = json.loads(raw)

    # ── Top-level metadata ────────────────────────────────────
    metadata = raw.get("metadata", {})
    info = raw.get("info", {})
    match_id = metadata.get("matchId", "unknown")
    game_version = info.get("gameVersion", "0.0")
    queue_id = info.get("queueId", 0)
    duration = info.get("gameDuration", 0)

    # Patch from gameVersion e.g. "14.10.123.456" → "14.10"
    parts = game_version.split(".")
    patch = f"{parts[0]}.{parts[1]}" if len(parts) >= 2 else game_version

    participants = info.get("participants", [])
    if not participants:
        return {
            "match_id": match_id,
            "patch": patch,
            "queue_id": queue_id,
            "duration": duration,
            "draft": None,
            "build": [],
            "performance": [],
        }

    # ── Split by team ─────────────────────────────────────────
    blue_team = [p for p in participants if p.get("teamId") == 100]
    red_team = [p for p in participants if p.get("teamId") == 200]

    # ── Team kills for KP calculation ────────────────────────
    blue_kills = sum(p.get("kills", 0) for p in blue_team)
    red_kills = sum(p.get("kills", 0) for p in red_team)

    # ── Draft features ────────────────────────────────────────
    draft = _extract_draft(blue_team, red_team, match_id)

    # ── Per-player features ───────────────────────────────────
    build_features = []
    performance_features = []

    for p in participants:
        team_kills = blue_kills if p.get("teamId") == 100 else red_kills

        b = _extract_build(p, match_id, duration)
        if b:
            build_features.append(b)

        perf = _extract_performance(p, match_id, duration, team_kills)
        if perf:
            performance_features.append(perf)

    return {
        "match_id": match_id,
        "patch": patch,
        "queue_id": queue_id,
        "duration": duration,
        "draft": draft,
        "build": build_features,
        "performance": performance_features,
    }


def _extract_draft(blue_team: list, red_team: list, match_id: str) -> Optional[dict]:
    """
    Draft features: champion names for each team + winner.
    We store names (not IDs) because champion IDs need the DB to resolve.
    The data_loader handles ID resolution at training time.
    """
    if len(blue_team) != 5 or len(red_team) != 5:
        return None

    blue_champs = [p.get("championName", "") for p in blue_team]
    red_champs = [p.get("championName", "") for p in red_team]

    # Validate all champion names present
    if any(c == "" for c in blue_champs + red_champs):
        return None

    blue_won = any(p.get("win", False) for p in blue_team)

    return {
        "valid": True,
        "match_id": match_id,
        "blue_champs": blue_champs,
        "red_champs": red_champs,
        "winner": "blue" if blue_won else "red",
    }


def _extract_build(participant: dict, match_id: str, duration: int) -> Optional[dict]:
    """
    Build features: champion, position, items, runes, gold, cs, damage, win.
    One record per player per game.
    """
    champion = participant.get("championName", "")
    position = _normalise_position(participant.get("teamPosition", ""))
    win = bool(participant.get("win", False))

    # Core stats
    gold = participant.get("goldEarned", 0)
    cs = participant.get("totalMinionsKilled", 0)
    dmg = participant.get("totalDamageDealtToChampions", participant.get("totalDamageDealt", 0))

    items = _extract_items(participant)
    runes = _extract_runes(participant)

    if not champion:
        return None

    return {
        "valid": True,
        "match_id": match_id,
        "puuid": participant.get("puuid", ""),
        "champion": champion,
        "position": position,
        "item_ids": items,
        "rune_ids": runes,
        "gold": gold,
        "cs": cs,
        "dmg": dmg,
        "win": win,
    }


def _extract_performance(participant: dict, match_id: str, duration: int, team_kills: int) -> Optional[dict]:
    """
    Performance features: the 9 stats the performance model is trained on,
    plus position and champion for benchmarking context.
    """
    champion = participant.get("championName", "")
    position = _normalise_position(participant.get("teamPosition", ""))
    win = bool(participant.get("win", False))

    kills = participant.get("kills", 0)
    deaths = participant.get("deaths", 0)
    assists = participant.get("assists", 0)
    gold = participant.get("goldEarned", 0)
    cs = participant.get("totalMinionsKilled", 0)
    dmg = participant.get("totalDamageDealtToChampions", participant.get("totalDamageDealt", 0))
    vision = participant.get("visionScore", participant.get("wardsPlaced", 0))

    kda = _safe_kda(kills, deaths, assists)
    kp = _safe_kp(kills, assists, team_kills)

    if not champion:
        return None

    return {
        "valid": True,
        "match_id": match_id,
        "puuid": participant.get("puuid", ""),
        "champion": champion,
        "position": position,
        "win": win,
        # The 9 features the performance model uses (must match data_loader order)
        "kills": kills,
        "deaths": deaths,
        "assists": assists,
        "gold": gold,
        "cs": cs,
        "dmg_champs": dmg,
        "vision": vision,
        "kda": kda,
        "kp": kp,
        "duration": duration,
    }


# ── Batch extraction ──────────────────────────────────────────


def extract_batch(raw_matches: list[dict | str]) -> list[dict]:
    """
    Extract features from a list of raw match JSONs.
    Skips matches that fail extraction without raising.
    """
    results = []
    for raw in raw_matches:
        try:
            results.append(extract(raw))
        except Exception as e:
            print(f"[feature_extractor] Skipped match: {e}")
    return results


# ── Validation ────────────────────────────────────────────────


def validate_riot_match(raw: dict) -> tuple[bool, str]:
    """
    Check that a raw Riot JSON has the minimum required fields.
    Returns (is_valid, reason_if_invalid).
    """
    if "metadata" not in raw:
        return False, "Missing 'metadata' key"
    if "matchId" not in raw.get("metadata", {}):
        return False, "Missing 'metadata.matchId'"
    if "info" not in raw:
        return False, "Missing 'info' key"
    info = raw["info"]
    if "participants" not in info:
        return False, "Missing 'info.participants'"
    if len(info["participants"]) != 10:
        return False, f"Expected 10 participants, got {len(info['participants'])}"
    p = info["participants"][0]
    required = ["championName", "teamId", "teamPosition", "win", "kills", "deaths", "assists", "goldEarned"]
    for field in required:
        if field not in p:
            return False, f"Participant missing required field '{field}'"
    return True, ""


# ── Feature vector conversion ─────────────────────────────────
# These functions convert the extracted dicts into numpy arrays
# for use with the trained sklearn models. Called by the ingestion
# router and the datasets builder.


def to_draft_vector(draft_features: dict, mlb, champion_id_map: dict) -> Optional[Any]:
    """
    Convert draft features to a numpy array using a fitted MLB.
    champion_id_map: {champion_name: champion_id}
    """
    try:
        import numpy as np

        blue_ids = [champion_id_map[c] for c in draft_features["blue_champs"] if c in champion_id_map]
        red_ids = [champion_id_map[c] for c in draft_features["red_champs"] if c in champion_id_map]
        if len(blue_ids) != 5 or len(red_ids) != 5:
            return None
        blue_enc = mlb.transform([blue_ids])
        red_enc = mlb.transform([red_ids])
        return np.hstack([blue_enc, red_enc]).astype(np.float32)
    except Exception:
        return None


def to_build_vector(build_features: dict, encoders: dict) -> Optional[Any]:
    """
    Convert build features to a numpy array using fitted encoders.
    encoders: {"item_mlb", "rune_mlb", "pos_le", "champ_le"}
    """
    try:
        import numpy as np

        item_mlb = encoders["item_mlb"]
        rune_mlb = encoders["rune_mlb"]
        pos_le = encoders["pos_le"]
        champ_le = encoders["champ_le"]

        # Items stored as strings in the encoder
        item_ids = [str(i) for i in build_features["item_ids"]]
        item_enc = item_mlb.transform([item_ids])
        rune_enc = rune_mlb.transform([build_features["rune_ids"]])
        pos_enc = pos_le.transform([build_features["position"]]).reshape(-1, 1)

        # Champion name → ID needed — caller must resolve
        champ_id = build_features.get("champion_id")
        if champ_id is None:
            return None
        champ_enc = champ_le.transform([champ_id]).reshape(-1, 1)
        numeric = np.array(
            [
                [
                    build_features["gold"],
                    build_features["cs"],
                    build_features["dmg"],
                ]
            ],
            dtype=np.float32,
        )
        return np.hstack([item_enc, rune_enc, pos_enc, champ_enc, numeric]).astype(np.float32)
    except Exception:
        return None


def to_performance_vector(perf_features: dict, encoders: dict) -> Optional[Any]:
    """
    Convert performance features to a numpy array.
    encoders: {"pos_le", "champ_le"}
    Must match data_loader order: pos_enc, champ_enc, numeric(9)
    """
    try:
        import numpy as np

        pos_le = encoders["pos_le"]
        champ_le = encoders["champ_le"]

        pos_enc = pos_le.transform([perf_features["position"]]).reshape(-1, 1)
        champ_id = perf_features.get("champion_id")
        if champ_id is None:
            return None
        champ_enc = champ_le.transform([champ_id]).reshape(-1, 1)
        numeric = np.array(
            [
                [
                    perf_features["kills"],
                    perf_features["deaths"],
                    perf_features["assists"],
                    perf_features["gold"],
                    perf_features["cs"],
                    perf_features["dmg_champs"],
                    perf_features["vision"],
                    perf_features["kda"],
                    perf_features["kp"],
                ]
            ],
            dtype=np.float32,
        )
        return np.hstack([pos_enc, champ_enc, numeric]).astype(np.float32)
    except Exception:
        return None
