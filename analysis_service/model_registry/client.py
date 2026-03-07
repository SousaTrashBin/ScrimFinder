"""
model_registry/client.py
High-level client used by the three analysis services to load and
hot-reload scikit-learn models from the registry.

Usage (inside a service):
    from model_registry.client import RegistryClient

    client = RegistryClient(concern="draft")
    model  = client.get_model()   # returns the live sklearn pipeline
"""

import os
import pickle
import threading
import logging
from typing import Any, Optional

from model_registry.db import get_active_model

logger = logging.getLogger(__name__)

# How often (seconds) each service re-checks the registry for a new active model.
# Override via env var MODEL_RELOAD_INTERVAL.
DEFAULT_RELOAD_INTERVAL = int(os.environ.get("MODEL_RELOAD_INTERVAL", 600))  # 10 min


class RegistryClient:
    """
    Thread-safe client that keeps one sklearn model loaded in memory
    and silently hot-reloads it when the registry points to a new file.
    """

    def __init__(self, concern: str, reload_interval: int = DEFAULT_RELOAD_INTERVAL):
        self.concern = concern
        self.reload_interval = reload_interval

        self._lock = threading.RLock()
        self._model: Optional[Any] = None
        self._loaded_version: Optional[str] = None

        # Load immediately at startup
        self._reload()

        # Background thread polls the registry
        self._start_reload_thread()

    # ── Public API ───────────────────────────────────────────

    def get_model(self) -> Any:
        """
        Return the currently active sklearn model.
        Raises RuntimeError if no active model exists yet.
        """
        with self._lock:
            if self._model is None:
                raise RuntimeError(
                    f"No active model for concern='{self.concern}'. "
                    "Run the training pipeline first."
                )
            return self._model

    def current_version(self) -> Optional[str]:
        with self._lock:
            return self._loaded_version

    # ── Internals ────────────────────────────────────────────

    def _reload(self) -> None:
        """Check the registry and reload the model if the version changed."""
        record = get_active_model(self.concern)

        if record is None:
            logger.warning("No active model found for concern='%s'", self.concern)
            return

        version = record["version"]

        with self._lock:
            if version == self._loaded_version:
                return  # already up-to-date

            file_path = record["file_path"]
            if not os.path.exists(file_path):
                logger.error(
                    "Model file not found on disk: %s (concern=%s, version=%s)",
                    file_path, self.concern, version,
                )
                return

            try:
                with open(file_path, "rb") as f:
                    new_model = pickle.load(f)

                self._model = new_model
                self._loaded_version = version
                logger.info(
                    "Loaded model for concern='%s' version='%s' from %s",
                    self.concern, version, file_path,
                )
            except Exception as exc:
                logger.error(
                    "Failed to load model for concern='%s': %s", self.concern, exc
                )

    def _start_reload_thread(self) -> None:
        def loop():
            import time
            while True:
                time.sleep(self.reload_interval)
                try:
                    self._reload()
                except Exception as exc:
                    logger.error("Registry reload error for '%s': %s", self.concern, exc)

        t = threading.Thread(target=loop, daemon=True, name=f"registry-reload-{self.concern}")
        t.start()