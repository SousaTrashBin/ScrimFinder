"""
services/performance/model.py
Loads the active performance model (pipeline + percentile table) from
the registry and exposes inference helpers.
"""

import numpy as np
from model_registry.client import RegistryClient
from services.performance.schemas import PerformanceMetric

_client = RegistryClient(concern="performance")


def _artifact():
    """Returns {"pipeline": sklearn_pipeline, "percentiles": dict}."""
    return _client.get_model()


def compute_metrics(stats: dict, tier: str) -> dict[str, PerformanceMetric]:
    """
    Given a dict of raw stats and the player's tier, return benchmarked
    PerformanceMetric objects for each tracked stat.

    stats keys: kda, avgDamageDealt, avgDamageTaken, avgVisionScore, avgGoldPerMinute
    """
    artifact = _artifact()
    percentiles = artifact.get("percentiles", {})
    tier_data = percentiles.get(tier, percentiles.get("GOLD", {}))

    result = {}
    for metric, value in stats.items():
        if metric not in tier_data:
            continue
        p = tier_data[metric]
        tier_avg = p.get("p50", value)
        # Compute approximate percentile rank
        if value <= p.get("p10", 0):
            pct = 10.0
        elif value <= p.get("p25", 0):
            pct = 25.0
        elif value <= p.get("p50", 0):
            pct = 50.0
        elif value <= p.get("p75", 0):
            pct = 75.0
        else:
            pct = 90.0

        result[metric] = PerformanceMetric(
            value=round(value, 2),
            tierAverage=round(tier_avg, 2),
            percentile=pct,
        )
    return result


def current_version() -> str | None:
    return _client.current_version()