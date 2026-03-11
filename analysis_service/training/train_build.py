"""
training/train_build.py
Build effectiveness model — items + runes + champion + position.
"""

import os
import pickle
from datetime import datetime, timezone

from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, roc_auc_score

#from model_registry.db import register_model, activate_model
from runner import TrainingJob
from data_loader import load_build_data

MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    version = _make_version()
    job.model_version = version
    report = job.update_progress

    # ── 1. Load data (0-95%) ──────────────────────────────────
    X, y, encoders = load_build_data(report)

    # ── 2. Split ──────────────────────────────────────────────
    report(95, "Splitting train / test")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train ──────────────────────────────────────────────
    report(96, f"Training RandomForest on {len(X_train):,} rows (this is the slow part) ...")
    clf = RandomForestClassifier(
        n_estimators=300,
        max_depth=10,
        min_samples_leaf=20,
        random_state=42,
        n_jobs=-1,
    )
    clf.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating model")
    y_pred = clf.predict(X_test)
    y_prob = clf.predict_proba(X_test)[:, 1]
    metrics = {
        "accuracy":    round(float(accuracy_score(y_test, y_pred)), 4),
        "roc_auc":     round(float(roc_auc_score(y_test, y_prob)), 4),
        "train_samples": int(len(X_train)),
        "test_samples":  int(len(X_test)),
        "n_items":       int(len(encoders["item_mlb"].classes_)),
        "n_runes":       int(len(encoders["rune_mlb"].classes_)),
        "n_features":    int(X.shape[1]),
    }
    job.metrics = metrics

    # ── 5. Persist ────────────────────────────────────────────
    report(99, "Saving model artifact")
    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"build_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump({"model": clf, "encoders": encoders}, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="build", version=version,
        file_path=os.path.abspath(file_path), metrics=metrics,
    )
    activate_model(model_id)
    report(100, f"Done — accuracy {metrics['accuracy']}  roc_auc {metrics['roc_auc']}")


def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"
