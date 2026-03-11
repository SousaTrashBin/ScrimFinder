"""
registry/client.py
Loads and caches model artifacts from disk.

Keeps a cache of loaded .pkl files so analysis endpoints don't
reload from disk on every request. Cache is invalidated when a new
model is activated (checked every MODEL_RELOAD_INTERVAL seconds).
"""

import pickle
import threading
import time
from typing import Optional
from analysis_api.core.config import cfg
from core import db


class _ModelCache:
    def __init__(self):
        self._cache: dict[str, tuple[int, object]] = {}  # concern → (model_id, artifact)
        self._lock = threading.Lock()
        self._last_check: float = 0.0

    def get(self, concern: str, model_id: int = None) -> Optional[object]:
        """Return artifact for concern (or specific model_id), loading from disk if needed."""
        self._maybe_refresh()
        with self._lock:
            if model_id is not None:
                # Load specific model by ID (bypasses concern cache)
                return self._load_by_id(model_id)
            entry = self._cache.get(concern)
            return entry[1] if entry else None

    def _maybe_refresh(self) -> None:
        now = time.time()
        if now - self._last_check < cfg.MODEL_RELOAD_INTERVAL:
            return
        self._last_check = now
        for concern in ("draft", "build", "performance", "champion"):
            row = db.get_active_model(concern)
            if row is None:
                with self._lock:
                    self._cache.pop(concern, None)
                continue
            with self._lock:
                cached = self._cache.get(concern)
                if cached and cached[0] == row["id"]:
                    continue  # Already loaded
            artifact = self._load_from_path(row["file_path"])
            if artifact is not None:
                with self._lock:
                    self._cache[concern] = (row["id"], artifact)

    def _load_by_id(self, model_id: int) -> Optional[object]:
        row = db.get_model(model_id)
        if row is None:
            return None
        return self._load_from_path(row["file_path"])

    @staticmethod
    def _load_from_path(path: str) -> Optional[object]:
        try:
            with open(path, "rb") as f:
                return pickle.load(f)
        except Exception as e:
            print(f"[registry] Failed to load model from {path}: {e}")
            return None


_cache = _ModelCache()


def get_loaded_model(concern: str, model_id: int = None) -> Optional[object]:
    """Public API — returns the loaded artifact dict or None."""
    db.init_db()  # Ensure tables exist before querying
    return _cache.get(concern, model_id=model_id)
