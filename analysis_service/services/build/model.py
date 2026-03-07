"""
services/build/model.py
Loads the active build model from the registry and exposes
inference helpers for the build analysis routes.
"""

import numpy as np
from model_registry.client import RegistryClient

_client = RegistryClient(concern="build")


def score_build(champion: str, items: list[str], enemy_comp: list[str] | None) -> tuple[int, float | None]:
    """
    Returns (score_0_to_100, win_rate_or_None).

    TODO: replace stub feature encoding with real item/champion embeddings
          matched to the feature space used during training.
    """
    clf = _client.get_model()

    # Stub feature vector: item hashes + champion hash + enemy hashes
    item_features = [hash(i) % 1000 / 1000.0 for i in items]
    champ_feature = [hash(champion) % 1000 / 1000.0]
    enemy_features = [hash(e) % 1000 / 1000.0 for e in (enemy_comp or [])]

    features = np.array(item_features + champ_feature + enemy_features, dtype=float).reshape(1, -1)

    n_features = 30
    if features.shape[1] < n_features:
        features = np.pad(features, ((0, 0), (0, n_features - features.shape[1])))
    else:
        features = features[:, :n_features]

    win_prob = float(clf.predict_proba(features)[0][1])
    score = int(round(win_prob * 100))
    return score, round(win_prob * 100, 1)


def current_version() -> str | None:
    return _client.current_version()