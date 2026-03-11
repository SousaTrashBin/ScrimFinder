"""
training/train_performance.py
Player performance scoring model — stats + objectives + percentile table.
"""

import os
import pickle
from datetime import datetime, timezone

from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

#from model_registry.db import register_model, activate_model
from runner import TrainingJob
from data_loader import load_performance_data

MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    version = _make_version()
    job.model_version = version
    report = job.update_progress

    # ── 1. Load data (0-95%) ──────────────────────────────────
    X, y, encoders, percentile_table = load_performance_data(report)

    # ── 2. Split ──────────────────────────────────────────────
    report(95, "Splitting train / test")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train ──────────────────────────────────────────────
    report(96, f"Training GBM pipeline on {len(X_train):,} rows ...")
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", GradientBoostingClassifier(
            n_estimators=300,
            learning_rate=0.05,
            max_depth=4,
            subsample=0.8,
            random_state=42,
        )),
    ])
    pipeline.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating model")
    y_pred = pipeline.predict(X_test)
    metrics = {
        "accuracy":    round(float(accuracy_score(y_test, y_pred)), 4),
        "f1_weighted": round(float(f1_score(y_test, y_pred, average="weighted")), 4),
        "train_samples": int(len(X_train)),
        "test_samples":  int(len(X_test)),
        "n_features":    int(X.shape[1]),
        "positions":     list(percentile_table.keys()),
    }
    job.metrics = metrics

    # ── 5. Persist ────────────────────────────────────────────
    report(99, "Saving model artifact")
    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"performance_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump({
            "pipeline":    pipeline,
            "encoders":    encoders,
            "percentiles": percentile_table,
        }, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="performance", version=version,
        file_path=os.path.abspath(file_path), metrics=metrics,
    )
    activate_model(model_id)
    report(100, f"Done — accuracy {metrics['accuracy']}")


def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"
