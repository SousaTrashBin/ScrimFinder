"""
model_registry/client.py
High-level client used by the analysis services to load and hot-reload
scikit-learn models from the registry.
"""

import os
import pickle
import threading
import logging
from typing import Any, Optional

from model_registry.db import init_db, get_active_model

logger = logging.getLogger(__name__)

DEFAULT_RELOAD_INTERVAL = int(os.environ.get("MODEL_RELOAD_INTERVAL", 600))


class RegistryClient:
    def __init__(self, concern: str, reload_interval: int = DEFAULT_RELOAD_INTERVAL):
        self.concern = concern
        self.reload_interval = reload_interval

        self._lock = threading.RLock()
        self._model: Optional[Any] = None
        self._loaded_version: Optional[str] = None

        # Ensure the registry DB and table exist before we try to query it
        try:
            init_db()
        except Exception as exc:
            logger.warning("init_db failed at startup for '%s': %s", concern, exc)

        self._reload()
        self._start_reload_thread()

    def get_model(self) -> Any:
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

    def _reload(self) -> None:
        try:
            record = get_active_model(self.concern)
        except Exception as exc:
            logger.warning("Registry query failed for '%s': %s", self.concern, exc)
            return

        if record is None:
            logger.warning("No active model found for concern='%s'", self.concern)
            return

        version = record["version"]

        with self._lock:
            if version == self._loaded_version:
                return

            file_path = record["file_path"]
            if not os.path.exists(file_path):
                logger.error("Model file not found: %s", file_path)
                return

            try:
                with open(file_path, "rb") as f:
                    self._model = pickle.load(f)
                self._loaded_version = version
                logger.info("Loaded model concern='%s' version='%s'", self.concern, version)
            except Exception as exc:
                logger.error("Failed to load model for '%s': %s", self.concern, exc)

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