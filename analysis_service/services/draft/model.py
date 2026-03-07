"""
services/draft/model.py
Loads the active draft model from the registry and exposes
inference helpers consumed by the FastAPI route handlers.
"""

import numpy as np
from model_registry.client import RegistryClient

# One shared client per process — hot-reloads in background
_client = RegistryClient(concern="draft")


def predict_win_probability(team_a_features: list, team_b_features: list) -> tuple[float, float]:
    """
    Returns (team_a_win_prob, team_b_win_prob).

    TODO: replace the feature stub with real champion/role encoding
          matched to the feature space used during training.
    """
    clf = _client.get_model()

    # Stub: concatenate feature vectors into one row
    features = np.array(team_a_features + team_b_features, dtype=float).reshape(1, -1)

    # Pad or trim to the expected number of features (20 in the stub training)
    n_features = 20
    if features.shape[1] < n_features:
        features = np.pad(features, ((0, 0), (0, n_features - features.shape[1])))
    else:
        features = features[:, :n_features]

    prob_b = float(clf.predict_proba(features)[0][1])
    prob_a = round(1.0 - prob_b, 4)
    return prob_a, round(prob_b, 4)


def current_version() -> str | None:
    return _client.current_version()