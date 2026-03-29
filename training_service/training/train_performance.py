"""
training_service/training/train_performance.py
Player performance classifier.
Input:  kills, deaths, assists, gold, cs, dmg_champs, vision, kda, kp, duration
Output: P(win) — used to benchmark individual performance against population
Algorithm: GBM inside a StandardScaler pipeline
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
    if algorithm in ("auto", "gbm", ""):
        return "gbm"
    if algorithm == "logistic":
        return "logistic"
    if algorithm == "random_forest":
        return "random_forest"
    return "gbm"


def train(job) -> None:
    report = job.update_progress
    filters = getattr(job, "filters", {})
    algorithm = _resolve_algorithm(getattr(job, "algorithm", "auto"))

    # ── 1. Load data ──────────────────────────────────────────
    report(0, "Connecting to database…")
    try:
        from training_service.training.data_loader import load_performance_data

        X, y, encoders, percentiles = load_performance_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    # ── 2. Split ──────────────────────────────────────────────
    from sklearn.metrics import accuracy_score, f1_score, roc_auc_score
    from sklearn.model_selection import train_test_split
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import StandardScaler

    report(93, "Splitting train/test")
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    n = len(X_tr)

    # ── 3. Train ──────────────────────────────────────────────
    if algorithm == "logistic":
        from sklearn.linear_model import LogisticRegression

        hyperparams = {
            "algorithm": "logistic_regression",
            "C": 1.0,
            "max_iter": 1000,
            "scaler": "standard",
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training Logistic Regression pipeline on {n:,} rows")
        pipe = Pipeline(
            [
                ("scaler", StandardScaler()),
                ("clf", LogisticRegression(C=1.0, max_iter=1000, solver="lbfgs")),
            ]
        )

    elif algorithm == "random_forest":
        from sklearn.ensemble import RandomForestClassifier

        trees = 50 if n < 20_000 else 100
        hyperparams = {
            "algorithm": "random_forest",
            "n_estimators": trees,
            "max_depth": 10,
            "min_samples_leaf": 20,
            "scaler": "standard",
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training Random Forest ({trees} trees) on {n:,} rows")
        pipe = Pipeline(
            [
                ("scaler", StandardScaler()),
                (
                    "clf",
                    RandomForestClassifier(
                        n_estimators=trees,
                        max_depth=10,
                        min_samples_leaf=20,
                        random_state=42,
                        n_jobs=-1,
                    ),
                ),
            ]
        )

    else:  # gbm
        from sklearn.ensemble import GradientBoostingClassifier

        trees = 30 if n < 5_000 else 50 if n < 20_000 else 100
        hyperparams = {
            "algorithm": "gradient_boosting",
            "n_estimators": trees,
            "learning_rate": 0.05,
            "max_depth": 4,
            "subsample": 0.8,
            "scaler": "standard",
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training GBM pipeline ({trees} trees) on {n:,} rows")
        pipe = Pipeline(
            [
                ("scaler", StandardScaler()),
                (
                    "clf",
                    GradientBoostingClassifier(
                        n_estimators=trees,
                        learning_rate=0.05,
                        max_depth=4,
                        subsample=0.8,
                        random_state=42,
                    ),
                ),
            ]
        )

    pipe.fit(X_tr, y_tr)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating")
    y_pred = pipe.predict(X_te)
    y_prob = pipe.predict_proba(X_te)[:, 1]
    metrics = {
        "accuracy": round(float(accuracy_score(y_te, y_pred)), 4),
        "f1_weighted": round(float(f1_score(y_te, y_pred, average="weighted")), 4),
        "roc_auc": round(float(roc_auc_score(y_te, y_prob)), 4),
        "train_samples": int(len(X_tr)),
        "test_samples": int(len(X_te)),
    }
    job.metrics = metrics

    # ── 5. Feature names ──────────────────────────────────────
    feature_names = [
        "kills",
        "deaths",
        "assists",
        "gold",
        "cs",
        "dmg_champs",
        "vision",
        "kda",
        "kp",
    ]

    # ── 6. Save ───────────────────────────────────────────────
    report(99, "Saving artifact")
    v = _version()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"performance_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump(
            {
                "pipeline": pipe,
                "encoders": encoders,
                "percentiles": percentiles,
            },
            f,
        )

    mid = db.register_model(
        concern="performance",
        algorithm=algorithm,
        version=v,
        file_path=os.path.abspath(path),
        metrics=metrics,
        hyperparams=hyperparams,
        feature_names=feature_names,
    )
    db.activate_model(mid)
    job.model_id = mid
    report(
        100,
        f"Done — accuracy={metrics['accuracy']} f1={metrics['f1_weighted']} roc_auc={metrics['roc_auc']}",
    )
