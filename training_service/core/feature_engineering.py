"""
training_service/core/feature_engineering.py

Bridges feature_extractor.py (Riot JSON → structured dicts) with the ML training pipeline
(structured dicts → flat numpy vectors).

This module is the glue between ingestion (which produces rich structured features) and
training (which needs flat float vectors with consistent feature_names).
"""

from __future__ import annotations

from typing import Optional

import numpy as np

from training_service.ingestion.feature_extractor import extract, validate_riot_match


# ═════════════════════════════════════════════════════════════
# Public API used by routers + training pipeline
# ═════════════════════════════════════════════════════════════


def extract_features(raw: dict, concern: str) -> tuple[list[float], list[str]]:
    """
    Extract a flat feature vector for a given concern from raw match data.

    This is the MAIN entry point. It delegates to feature_extractor.extract() for
    structured parsing, then flattens the result into (vector, names) tuples
    compatible with sklearn / the DB features table.

    Returns (feature_vector, feature_names).
    """
    # 1. Validate & extract structured features
    valid, reason = validate_riot_match(raw)
    if not valid:
        # Return a sentinel so callers can decide to skip or error
        return [0.0], [f"invalid:{reason}"]

    structured = extract(raw)

    # 2. Route to the correct flattener
    if concern == "draft":
        return _flatten_draft(structured.get("draft"))
    elif concern == "build":
        # Build is per-player; for a single vector we aggregate across all 10 players
        return _flatten_build_aggregate(structured.get("build", []))
    elif concern == "performance":
        # Performance is per-player; aggregate across all 10 players
        return _flatten_performance_aggregate(structured.get("performance", []))
    else:
        return [0.0], ["unknown_concern"]


def extract_features_batch(
    raw_matches: list[dict], concern: str
) -> list[tuple[list[float], list[str]]]:
    """Batch version for efficient ingestion of multiple matches."""
    results = []
    for raw in raw_matches:
        try:
            results.append(extract_features(raw, concern))
        except Exception as exc:
            results.append(([0.0], [f"error:{exc}"]))
    return results


# ═════════════════════════════════════════════════════════════
# Flatteners — structured dict → (vector, names)
# ═════════════════════════════════════════════════════════════


def _flatten_draft(draft: Optional[dict]) -> tuple[list[float], list[str]]:
    """
    Draft structured format:
      {"blue_champs": ["A","B","C","D","E"], "red_champs": ["F","G","H","I","J"], "winner": "blue"}

    Flat vector: 10 champion slots + 1 winner indicator.
    NOTE: champion names are hashed to stable floats because the training pipeline
    uses MultiLabelBinarizer at data-loader time. This vector is a *preview*;
    the real training vectors are built by data_loader.load_draft_data().
    """
    if draft is None or not draft.get("valid"):
        return [0.0] * 11, [
            "blue_0",
            "blue_1",
            "blue_2",
            "blue_3",
            "blue_4",
            "red_0",
            "red_1",
            "red_2",
            "red_3",
            "red_4",
            "winner",
        ]

    blue = draft.get("blue_champs", [])
    red = draft.get("red_champs", [])
    winner = 1.0 if draft.get("winner") == "blue" else 0.0

    # Hash champion names to stable floats (0-1 range) for preview vectors
    def _hash_champ(name: str) -> float:
        return (hash(name) % 10000) / 10000.0

    vector = [_hash_champ(c) for c in (blue + [""] * 5)[:5]]
    vector += [_hash_champ(c) for c in (red + [""] * 5)[:5]]
    vector.append(winner)

    names = (
        [f"blue_{i}" for i in range(5)] + [f"red_{i}" for i in range(5)] + ["winner"]
    )
    return vector, names


def _flatten_build_aggregate(builds: list[dict]) -> tuple[list[float], list[str]]:
    """
    Build is per-player. Aggregate into a single team-level vector:
      - avg gold, avg cs, avg dmg
      - item diversity (unique items / total slots)
      - rune diversity
      - win rate
    """
    if not builds:
        return [0.0] * 6, [
            "avg_gold",
            "avg_cs",
            "avg_dmg",
            "item_diversity",
            "rune_diversity",
            "win_rate",
        ]

    golds = [b.get("gold", 0) for b in builds]
    css = [b.get("cs", 0) for b in builds]
    dmgs = [b.get("dmg", 0) for b in builds]
    all_items = set()
    total_item_slots = 0
    all_runes = set()
    total_rune_slots = 0
    wins = sum(1 for b in builds if b.get("win"))

    for b in builds:
        items = b.get("item_ids", [])
        runes = b.get("rune_ids", [])
        all_items.update(str(i) for i in items if i)
        total_item_slots += len(items)
        all_runes.update(str(r) for r in runes if r)
        total_rune_slots += len(runes)

    n = len(builds)
    vector = [
        round(sum(golds) / n, 1),
        round(sum(css) / n, 1),
        round(sum(dmgs) / n, 1),
        round(len(all_items) / max(total_item_slots, 1), 4),
        round(len(all_runes) / max(total_rune_slots, 1), 4),
        round(wins / n, 4),
    ]
    names = [
        "avg_gold",
        "avg_cs",
        "avg_dmg",
        "item_diversity",
        "rune_diversity",
        "win_rate",
    ]
    return vector, names


def _flatten_performance_aggregate(perfs: list[dict]) -> tuple[list[float], list[str]]:
    """
    Performance is per-player. Aggregate into team-level stats:
      - avg kills, deaths, assists, gold, cs, dmg, vision, kda, kp
    """
    if not perfs:
        return [0.0] * 9, [
            "avg_kills",
            "avg_deaths",
            "avg_assists",
            "avg_gold",
            "avg_cs",
            "avg_dmg",
            "avg_vision",
            "avg_kda",
            "avg_kp",
        ]

    keys = [
        "kills",
        "deaths",
        "assists",
        "gold",
        "cs",
        "dmg_champs",
        "vision",
        "kda",
        "kp",
    ]
    len(perfs)
    vector = []
    for k in keys:
        vals = [p.get(k, 0) for p in perfs if p.get(k) is not None]
        vector.append(round(sum(vals) / max(len(vals), 1), 2))

    names = [f"avg_{k}" for k in keys]
    return vector, names


# ═════════════════════════════════════════════════════════════
# Training-pipeline helpers (called by data_loader.py)
# ═════════════════════════════════════════════════════════════


def build_draft_vectors(
    matches: list[dict], champion_id_map: dict
) -> tuple[np.ndarray, np.ndarray, list[str]]:
    """
    Convert a list of raw matches into X, y arrays for draft training.

    Args:
        matches: list of raw Riot JSON dicts
        champion_id_map: {champion_name: champion_id}  (from dim_champions)

    Returns:
        X: np.ndarray shape (n_matches, n_champions * 2)
        y: np.ndarray shape (n_matches,)  (1 = blue wins)
        feature_names: list of champion IDs as strings
    """
    from sklearn.preprocessing import MultiLabelBinarizer

    blue_teams, red_teams, winners = [], [], []

    for raw in matches:
        structured = extract(raw)
        draft = structured.get("draft")
        if not draft or not draft.get("valid"):
            continue

        blue_ids = [champion_id_map.get(c) for c in draft["blue_champs"]]
        red_ids = [champion_id_map.get(c) for c in draft["red_champs"]]
        if (
            None in blue_ids
            or None in red_ids
            or len(blue_ids) != 5
            or len(red_ids) != 5
        ):
            continue

        blue_teams.append(blue_ids)
        red_teams.append(red_ids)
        winners.append(1.0 if draft["winner"] == "blue" else 0.0)

    if not blue_teams:
        raise ValueError("No valid draft matches after champion ID resolution.")

    all_champs = sorted({c for team in blue_teams + red_teams for c in team})
    mlb = MultiLabelBinarizer(classes=all_champs)
    mlb.fit(all_champs)

    blue_enc = mlb.transform(blue_teams)
    red_enc = mlb.transform(red_teams)
    X = np.hstack([blue_enc, red_enc]).astype(np.float32)
    y = np.array(winners, dtype=np.int32)
    feature_names = [f"blue_{c}" for c in mlb.classes_] + [
        f"red_{c}" for c in mlb.classes_
    ]

    return X, y, feature_names


def build_performance_vectors(
    perfs: list[dict], pos_le, champ_le
) -> tuple[np.ndarray, np.ndarray]:
    """
    Convert performance feature dicts into X, y arrays.

    Args:
        perfs: list of performance feature dicts (from feature_extractor)
        pos_le: fitted LabelEncoder for positions
        champ_le: fitted LabelEncoder for champion IDs

    Returns:
        X: np.ndarray shape (n_samples, n_features)
        y: np.ndarray shape (n_samples,)
    """
    rows = []
    labels = []
    for p in perfs:
        if not p or not p.get("valid"):
            continue
        try:
            pos_enc = pos_le.transform([p["position"]]).reshape(-1, 1)
            champ_enc = champ_le.transform([p["champion_id"]]).reshape(-1, 1)
            numeric = np.array(
                [
                    [
                        p["kills"],
                        p["deaths"],
                        p["assists"],
                        p["gold"],
                        p["cs"],
                        p["dmg_champs"],
                        p["vision"],
                        p["kda"],
                        p["kp"],
                    ]
                ],
                dtype=np.float32,
            )
            row = np.hstack([pos_enc, champ_enc, numeric]).astype(np.float32)
            rows.append(row[0])
            labels.append(1 if p.get("win") else 0)
        except Exception:
            continue

    if not rows:
        raise ValueError("No valid performance rows after encoding.")

    X = np.vstack(rows)
    y = np.array(labels, dtype=np.int32)
    return X, y
