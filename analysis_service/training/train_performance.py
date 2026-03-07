"""
training/train_performance.py
Trains a player performance scoring model.

The model learns a composite performance score from in-game stats,
benchmarked against tier peers. Used downstream to compute percentiles,
highlights/lowlights, and improvement tips.

Input features (per participant row):
  - kills, deaths, assists → KDA
  - damage dealt / taken
  - gold per minute
  - vision score
  - objective participation rate
  - tier (ordinal)
  - role (one-hot)

Target: win (binary) — high-performing players in wins score positively.
        A separate percentile lookup table is also saved alongside the model.

Replace stub loader with real EUW dataset reader.
"""

import os
import pickle
from datetime import datetime, timezone

import numpy as np
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline

from model_registry.db import register_model, activate_model
from training.runner import TrainingJob

MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    version = _make_version()
    job.model_version = version

    # ── 1. Load data ─────────────────────────────────────────
    # TODO: replace with real EUW dataset loader
    X, y = _stub_data()

    # ── 2. Split ──────────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train (Pipeline: scaler + GBM) ────────────────────
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", GradientBoostingClassifier(
            n_estimators=200,
            learning_rate=0.05,
            max_depth=4,
            random_state=42,
        )),
    ])
    pipeline.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    y_pred = pipeline.predict(X_test)
    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 4),
        "f1_weighted": round(float(f1_score(y_test, y_pred, average="weighted")), 4),
        "train_samples": int(len(X_train)),
        "test_samples": int(len(X_test)),
    }
    job.metrics = metrics

    # ── 5. Build percentile lookup table ─────────────────────
    # Maps tier → {metric → [p10, p25, p50, p75, p90]}
    # TODO: compute from real data grouped by tier
    percentile_table = _stub_percentile_table()

    # Save pipeline + percentile table together in one artifact
    artifact = {"pipeline": pipeline, "percentiles": percentile_table}

    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"performance_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump(artifact, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="performance",
        version=version,
        file_path=os.path.abspath(file_path),
        metrics=metrics,
    )
    activate_model(model_id)


def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"


def _stub_data():
    rng = np.random.default_rng(2)
    X = rng.random((1000, 10))
    y = (X[:, 0] + X[:, 3] > 1.0).astype(int)
    return X, y


def _stub_percentile_table() -> dict:
    """Stub percentile lookup. Replace with real tier-grouped stats."""
    tiers = ["IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM",
             "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"]
    metrics = ["kda", "avgDamageDealt", "avgVisionScore", "avgGoldPerMinute"]
    table = {}
    rng = np.random.default_rng(3)
    for tier in tiers:
        table[tier] = {}
        for metric in metrics:
            base = rng.uniform(1.5, 5.0)
            table[tier][metric] = {
                "p10": round(base * 0.5, 2),
                "p25": round(base * 0.75, 2),
                "p50": round(base, 2),
                "p75": round(base * 1.25, 2),
                "p90": round(base * 1.5, 2),
            }
    return table