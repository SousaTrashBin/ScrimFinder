"""
training_service/training/train_draft.py
Draft win-probability classifier.
Input:  10 champion IDs (5 blue + 5 red) encoded as multi-hot vectors
Output: P(blue team wins)
Algorithm: Gradient Boosting Machine (GBM)
"""

import os
import pickle
from datetime import datetime, timezone

from training_service.core import db
from training_service.core.config import cfg


def _version() -> str:
    now = datetime.now(timezone.utc)
    return f"{now.strftime('%Y-W%W')}-{now.strftime('%Y%m%dT%H%M%S')}"


def _resolve_algorithm(algorithm: str) -> str:
    """Map 'auto' to the default for this concern."""
    if algorithm in ("auto", "gbm", ""):
        return "gbm"
    if algorithm == "logistic":
        return "logistic"
    if algorithm == "lightgbm":
        return "lightgbm"
    return "gbm"


def train(job) -> None:
    report = job.update_progress
    filters = getattr(job, "filters", {})
    algorithm = _resolve_algorithm(getattr(job, "algorithm", "auto"))

    # ── 1. Load data ──────────────────────────────────────────
    report(0, "Connecting to database…")
    try:
        from training_service.training.data_loader import load_draft_data

        X, y, mlb = load_draft_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    # ── 2. Split ──────────────────────────────────────────────
    from sklearn.metrics import accuracy_score, f1_score, roc_auc_score
    from sklearn.model_selection import train_test_split

    report(93, "Splitting train/test")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    n = len(X_tr)

    # ── 3. Train ──────────────────────────────────────────────
    if algorithm == "logistic":
        from sklearn.linear_model import LogisticRegression

        hyperparams = {
            "C": 1.0,
            "max_iter": 1000,
            "solver": "lbfgs",
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training Logistic Regression on {n:,} rows")
        clf = LogisticRegression(C=1.0, max_iter=1000, solver="lbfgs")

    elif algorithm == "lightgbm":
        try:
            import lightgbm as lgb

            trees = 100
            hyperparams = {
                "n_estimators": trees,
                "learning_rate": 0.05,
                "max_depth": 6,
                "num_leaves": 31,
                "test_size": 0.2,
                "sample_fraction": filters.get("sample", 1.0),
                "train_samples": n,
            }
            report(95, f"Training LightGBM ({trees} trees) on {n:,} rows")
            clf = lgb.LGBMClassifier(
                n_estimators=trees, learning_rate=0.05, max_depth=6, num_leaves=31, random_state=42
            )
        except ImportError:
            algorithm = "gbm"  # fallback

    if algorithm == "gbm":
        from sklearn.ensemble import GradientBoostingClassifier

        trees = 30 if n < 5_000 else 50 if n < 20_000 else 100
        hyperparams = {
            "n_estimators": trees,
            "learning_rate": 0.05,
            "max_depth": 4,
            "subsample": 0.8,
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training GBM ({trees} trees) on {n:,} rows")
        clf = GradientBoostingClassifier(
            n_estimators=trees,
            learning_rate=0.05,
            max_depth=4,
            subsample=0.8,
            random_state=42,
        )

    clf.fit(X_tr, y_tr)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating")
    y_pred = clf.predict(X_te)
    y_prob = clf.predict_proba(X_te)[:, 1]
    metrics = {
        "accuracy": round(float(accuracy_score(y_te, y_pred)), 4),
        "roc_auc": round(float(roc_auc_score(y_te, y_prob)), 4),
        "f1": round(float(f1_score(y_te, y_pred)), 4),
        "train_samples": int(len(X_tr)),
        "test_samples": int(len(X_te)),
    }
    job.metrics = metrics

    # ── 5. Feature names ──────────────────────────────────────
    n_champs = len(mlb.classes_)
    feature_names = [f"blue_{c}" for c in mlb.classes_] + [f"red_{c}" for c in mlb.classes_]

    # ── 6. Save ───────────────────────────────────────────────
    report(99, "Saving artifact")
    v = _version()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"draft_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump({"model": clf, "mlb": mlb}, f)

    mid = db.register_model(
        concern="draft",
        algorithm=algorithm,
        version=v,
        file_path=os.path.abspath(path),
        metrics=metrics,
        hyperparams=hyperparams,
        feature_names=feature_names,
    )
    db.activate_model(mid)
    job.model_id = mid
    report(100, f"Done — accuracy={metrics['accuracy']} roc_auc={metrics['roc_auc']}")
