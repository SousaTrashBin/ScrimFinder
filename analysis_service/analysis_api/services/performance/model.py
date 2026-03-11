"""
services/performance/model.py
Loads the active performance artifact {pipeline, encoders, percentiles}
and exposes metric benchmarking.
"""

import numpy as np
from model_registry.client import RegistryClient
from services.performance.schemas import PerformanceMetric

_client = RegistryClient(concern="performance")


def _artifact():
    return _client.get_model()  # {"pipeline", "encoders", "percentiles"}


def compute_metrics(stats: dict, position: str) -> dict[str, PerformanceMetric]:
    """
    stats  — dict of raw metric values, e.g. {"kda": 3.2, "vision": 18, ...}
    position — mapped role string e.g. "BOT", "SUPPORT" (our Role enum values)

    Returns a dict of PerformanceMetric with value, tierAverage, and percentile.
    """
    artifact     = _artifact()
    percentiles  = artifact["percentiles"]
    pos_data     = percentiles.get(position, percentiles.get("BOT", {}))

    result = {}
    for metric, value in stats.items():
        if metric not in pos_data:
            continue
        p = pos_data[metric]
        tier_avg = p["p50"]

        # Simple lookup-based percentile rank
        if   value <= p["p10"]: pct = 10.0
        elif value <= p["p25"]: pct = 25.0
        elif value <= p["p50"]: pct = 50.0
        elif value <= p["p75"]: pct = 75.0
        else:                   pct = 90.0

        result[metric] = PerformanceMetric(
            value=round(float(value), 2),
            tierAverage=round(float(tier_avg), 2),
            percentile=pct,
        )
    return result


def current_version() -> str | None:
    return _client.current_version()
