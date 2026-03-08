"""
training/train_draft.py
Win-probability classifier for draft compositions.
"""

import os
import pickle
from datetime import datetime, timezone

from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score

from model_registry.db import register_model, activate_model
from training.runner import TrainingJob
from training.data_loader import load_draft_data

MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    version = _make_version()
    job.model_version = version
    report = job.update_progress

    # ── 1. Load data (0-95%) ──────────────────────────────────
    X, y, mlb = load_draft_data(report)

    # ── 2. Split ──────────────────────────────────────────────
    report(95, "Splitting train / test")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train ──────────────────────────────────────────────
    report(96, "Training GradientBoosting (300 trees) ...")
    clf = GradientBoostingClassifier(
        n_estimators=300,
        learning_rate=0.05,
        max_depth=3,
        subsample=0.8,
        random_state=42,
    )
    clf.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating model")
    y_pred = clf.predict(X_test)
    metrics = {
        "accuracy":    round(float(accuracy_score(y_test, y_pred)), 4),
        "f1_weighted": round(float(f1_score(y_test, y_pred, average="weighted")), 4),
        "train_samples": int(len(X_train)),
        "test_samples":  int(len(X_test)),
        "n_champions":   int(len(mlb.classes_)),
        "n_features":    int(X.shape[1]),
    }
    job.metrics = metrics

    # ── 5. Persist ────────────────────────────────────────────
    report(99, "Saving model artifact")
    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"draft_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump({"model": clf, "mlb": mlb}, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="draft", version=version,
        file_path=os.path.abspath(file_path), metrics=metrics,
    )
    activate_model(model_id)
    report(100, f"Done — accuracy {metrics['accuracy']}")


def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"