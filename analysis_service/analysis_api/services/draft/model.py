"""
services/draft/model.py
Loads the active draft artifact {model, mlb} from the registry
and exposes win-probability inference.
"""

import numpy as np
from model_registry.client import RegistryClient

_client = RegistryClient(concern="draft")


def _artifact():
    return _client.get_model()   # {"model": GBM, "mlb": MultiLabelBinarizer}


def predict_win_probability(
    blue_champions: list[int],
    red_champions: list[int],
) -> tuple[float, float]:
    """
    champion IDs (ints) → (blue_win_prob, red_win_prob)
    """
    artifact = _artifact()
    clf = artifact["model"]
    mlb = artifact["mlb"]

    blue_enc = mlb.transform([blue_champions])
    red_enc  = mlb.transform([red_champions])
    features = np.hstack([blue_enc, red_enc]).astype(np.float32)

    prob_red  = float(clf.predict_proba(features)[0][1])
    prob_blue = round(1.0 - prob_red, 4)
    return prob_blue, round(prob_red, 4)


def current_version() -> str | None:
    return _client.current_version()
