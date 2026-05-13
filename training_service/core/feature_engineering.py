"""
training_service/core/feature_engineering.py

Dummy feature extraction for tests.
Real implementation would do actual ML feature engineering.
"""


def extract_features(raw: dict, concern: str) -> tuple[list[float], list[str]]:
    """
    Extract a feature vector for a given concern from raw match data.

    Returns (feature_vector, feature_names).
    """
    # Minimal dummy vectors so tests have something to validate
    if concern == "draft":
        return [1.0, 0.5, 0.2], ["team_comp_score", "synergy", "counter"]
    elif concern == "build":
        return [0.8, 0.3], ["item_efficiency", "rune_synergy"]
    elif concern == "performance":
        return [0.9, 0.7, 0.4, 0.1], [
            "kda",
            "cs_per_min",
            "vision_score",
            "objective_control",
        ]
    else:
        return [0.0], ["unknown"]
