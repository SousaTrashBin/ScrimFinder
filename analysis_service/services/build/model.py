"""
services/build/model.py
Loads the active build artifact {model, encoders} from the registry
and exposes build scoring inference.
"""

import numpy as np
from model_registry.client import RegistryClient

_client = RegistryClient(concern="build")


def _artifact():
    return _client.get_model()   # {"model": RF, "encoders": {item_mlb, rune_mlb, pos_le, champ_le}}


def score_build(
    champion_id: int,
    position: str,
    item_ids: list[int],
    rune_ids: list[int],
    gold: float = 0,
    cs: float = 0,
    dmg_champs: float = 0,
) -> tuple[int, float]:
    """
    Returns (score_0_to_100, win_rate_pct).
    champion_id and item/rune ids must match the values in dim_champions/dim_items/dim_runes.
    """
    artifact = _artifact()
    clf      = artifact["model"]
    enc      = artifact["encoders"]

    item_enc  = enc["item_mlb"].transform([item_ids])
    rune_enc  = enc["rune_mlb"].transform([rune_ids])
    pos_enc   = enc["pos_le"].transform([position]).reshape(-1, 1)
    champ_enc = enc["champ_le"].transform([champion_id]).reshape(-1, 1)
    numeric   = np.array([[gold, cs, dmg_champs]], dtype=np.float32)

    features = np.hstack([item_enc, rune_enc, pos_enc, champ_enc, numeric]).astype(np.float32)

    win_prob  = float(clf.predict_proba(features)[0][1])
    score     = int(round(win_prob * 100))
    return score, round(win_prob * 100, 1)


def current_version() -> str | None:
    return _client.current_version()