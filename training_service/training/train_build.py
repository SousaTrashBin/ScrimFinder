"""
training_service/training/train_build.py
Build effectiveness classifier.
Input:  champion_id + position + items (multi-hot) + runes (multi-hot) + gold/cs/damage
Output: P(win) given this build
Algorithm: Random Forest (default) or GBM
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
    if algorithm in ("auto", "random_forest", ""):
        return "random_forest"
    if algorithm == "gbm":
        return "gbm"
    if algorithm == "lightgbm":
        return "lightgbm"
    return "random_forest"


def train(job) -> None:
    report = job.update_progress
    filters = getattr(job, "filters", {})
    algorithm = _resolve_algorithm(getattr(job, "algorithm", "auto"))

    # ── 1. Load data ──────────────────────────────────────────
    report(0, "Connecting to database…")
    try:
        from training_service.training.data_loader import load_build_data

        X, y, encoders = load_build_data(report, filters)
    except Exception as e:
        raise RuntimeError(f"Data loading failed: {e}") from e

    # ── 2. Split ──────────────────────────────────────────────
    from sklearn.metrics import accuracy_score, roc_auc_score
    from sklearn.model_selection import train_test_split

    report(93, "Splitting train/test")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    n = len(X_tr)

    # ── 3. Train ──────────────────────────────────────────────
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
            algorithm = "random_forest"

    if algorithm == "random_forest":
        from sklearn.ensemble import RandomForestClassifier

        trees = 20 if n < 5_000 else 50 if n < 20_000 else 100
        max_depth = 10
        min_samples_leaf = 20
        hyperparams = {
            "n_estimators": trees,
            "max_depth": max_depth,
            "min_samples_leaf": min_samples_leaf,
            "n_jobs": -1,
            "test_size": 0.2,
            "sample_fraction": filters.get("sample", 1.0),
            "train_samples": n,
        }
        report(95, f"Training Random Forest ({trees} trees) on {n:,} rows")
        clf = RandomForestClassifier(
            n_estimators=trees,
            max_depth=max_depth,
            min_samples_leaf=min_samples_leaf,
            random_state=42,
            n_jobs=-1,
        )

    clf.fit(X_tr, y_tr)

    # ── 4. Evaluate ───────────────────────────────────────────
    report(98, "Evaluating")
    y_pred = clf.predict(X_te)
    y_prob = clf.predict_proba(X_te)[:, 1]
    metrics = {
        "accuracy": round(float(accuracy_score(y_te, y_pred)), 4),
        "roc_auc": round(float(roc_auc_score(y_te, y_prob)), 4),
        "train_samples": int(len(X_tr)),
        "test_samples": int(len(X_te)),
    }
    job.metrics = metrics

    # ── 5. Feature names ──────────────────────────────────────
    item_mlb = encoders["item_mlb"]
    rune_mlb = encoders["rune_mlb"]
    pos_le = encoders["pos_le"]
    champ_le = encoders["champ_le"]

    feature_names = (
        [f"item_{i}" for i in item_mlb.classes_]
        + [f"rune_{r}" for r in rune_mlb.classes_]
        + ["position", "champion_id"]
        + ["gold", "cs", "dmg_champs"]
    )

    # ── 6. Save ───────────────────────────────────────────────
    report(99, "Saving artifact")
    v = _version()
    os.makedirs(cfg.MODELS_DIR, exist_ok=True)
    path = os.path.join(cfg.MODELS_DIR, f"build_{v}.pkl")
    with open(path, "wb") as f:
        pickle.dump({"model": clf, "encoders": encoders}, f)

    mid = db.register_model(
        concern="build",
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
