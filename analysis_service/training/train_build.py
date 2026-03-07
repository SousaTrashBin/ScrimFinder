"""
training/train_build.py
Trains a build effectiveness regression/scoring model.

Input features (per participant row in the EUW dataset):
  - item one-hot encoding (6 item slots)
  - champion identity
  - role
  - enemy team composition flags
  - patch version

Target: win (binary) — we predict win probability given a build
        as a proxy for build effectiveness score.

Replace the stub loader with your real EUW dataset reader.
"""

import os
import pickle
from datetime import datetime, timezone

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, roc_auc_score

from model_registry.db import register_model, activate_model
from training.runner import TrainingJob

MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    version = _make_version()
    job.model_version = version

    # ── 1. Load data ─────────────────────────────────────────
    # TODO: replace with real EUW dataset loader
    # e.g. df = pd.read_parquet("/data/euw_matches.parquet")
    # X = build_feature_engineering(df)
    # y = df["win"].values
    X, y = _stub_data()

    # ── 2. Split ──────────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train ──────────────────────────────────────────────
    clf = RandomForestClassifier(
        n_estimators=300,
        max_depth=8,
        min_samples_leaf=10,
        random_state=42,
        n_jobs=-1,
    )
    clf.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    y_pred = clf.predict(X_test)
    y_prob = clf.predict_proba(X_test)[:, 1]
    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 4),
        "roc_auc": round(float(roc_auc_score(y_test, y_prob)), 4),
        "train_samples": int(len(X_train)),
        "test_samples": int(len(X_test)),
    }
    job.metrics = metrics

    # ── 5. Persist ────────────────────────────────────────────
    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"build_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump(clf, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="build",
        version=version,
        file_path=os.path.abspath(file_path),
        metrics=metrics,
    )
    activate_model(model_id)


def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"


def _stub_data():
    rng = np.random.default_rng(1)
    X = rng.random((800, 30))
    y = (X[:, 0] * 2 + X[:, 2] > 1.5).astype(int)
    return X, y