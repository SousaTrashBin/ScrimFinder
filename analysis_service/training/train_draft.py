"""
training/train_draft.py
Trains a win-probability classifier for draft compositions.

Input features (per match row in the EUW dataset):
  - champion one-hot encoding for each of the 10 picks
  - role assignment flags
  - patch version (ordinal encoded)

Target: match winner (0 = blue, 1 = red)

Replace the stub data loading with your actual EUW parquet/CSV reader.
"""

import os
import pickle
from datetime import datetime, timezone

import numpy as np
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score
from sklearn.preprocessing import LabelEncoder

from model_registry.db import register_model, activate_model
from training.runner import TrainingJob

# Where to save model artifacts
MODELS_DIR = os.environ.get("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))


def train(job: TrainingJob) -> None:
    """
    Full training cycle for the draft model.
    Called by the runner; updates job with version + metrics when done.
    """
    version = _make_version()
    job.model_version = version

    # ── 1. Load data ─────────────────────────────────────────
    # TODO: replace with real EUW dataset loader
    # e.g. df = pd.read_parquet("/data/euw_matches.parquet")
    # X = feature_engineering(df)
    # y = df["winner"].map({"blue": 0, "red": 1}).values
    X, y = _stub_data()

    # ── 2. Split ──────────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # ── 3. Train ──────────────────────────────────────────────
    clf = GradientBoostingClassifier(
        n_estimators=200,
        learning_rate=0.05,
        max_depth=4,
        random_state=42,
    )
    clf.fit(X_train, y_train)

    # ── 4. Evaluate ───────────────────────────────────────────
    y_pred = clf.predict(X_test)
    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 4),
        "f1_weighted": round(float(f1_score(y_test, y_pred, average="weighted")), 4),
        "train_samples": int(len(X_train)),
        "test_samples": int(len(X_test)),
    }
    job.metrics = metrics

    # ── 5. Persist ────────────────────────────────────────────
    os.makedirs(MODELS_DIR, exist_ok=True)
    file_path = os.path.join(MODELS_DIR, f"draft_{version}.pkl")
    with open(file_path, "wb") as f:
        pickle.dump(clf, f)

    # ── 6. Register + activate ────────────────────────────────
    model_id = register_model(
        concern="draft",
        version=version,
        file_path=os.path.abspath(file_path),
        metrics=metrics,
    )
    activate_model(model_id)


# ── Helpers ───────────────────────────────────────────────────

def _make_version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"


def _stub_data():
    """
    Stub: 500 random samples with 20 features.
    Replace with real feature engineering from the EUW dataset.
    """
    rng = np.random.default_rng(0)
    X = rng.random((500, 20))
    y = (X[:, 0] + X[:, 1] > 1.0).astype(int)
    return X, y