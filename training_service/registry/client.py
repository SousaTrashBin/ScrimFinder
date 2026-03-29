import pickle
import threading
from pathlib import Path
from typing import Optional

from training_service.core import db
from training_service.core.config import cfg


class RegistryClient:
    def __init__(self, concern: str):
        self._concern = concern
        self._artifact = None
        self._version: Optional[str] = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        db.init_db()
        self._load()
        self._start_watcher()

    def get_model(self) -> dict:
        with self._lock:
            if self._artifact is None:
                raise RuntimeError(f"No active model for concern='{self._concern}'.")
            return self._artifact

    def current_version(self) -> Optional[str]:
        with self._lock:
            return self._version

    def is_ready(self) -> bool:
        with self._lock:
            return self._artifact is not None

    def _load(self):
        row = db.get_active_model(self._concern)
        if not row:
            return
        path_str = row.get("file_path")
        if not path_str:
            return
        path = Path(path_str)
        if not path.exists():
            return
        try:
            with path.open("rb") as f:
                art = pickle.load(f)
            with self._lock:
                self._artifact = art
                self._version = row["version"]
        except Exception as e:
            print(f"[RegistryClient:{self._concern}] Load failed: {e}")

    def _start_watcher(self):
        def loop():
            while not self._stop.wait(timeout=cfg.MODEL_RELOAD_INTERVAL):
                row = db.get_active_model(self._concern)
                if row and row.get("version") != self._version:
                    self._load()

        threading.Thread(
            target=loop, daemon=True, name=f"watcher-{self._concern}"
        ).start()

    def stop(self):
        self._stop.set()


_clients: dict = {}
_lock = threading.Lock()


def get_client(concern: str) -> RegistryClient:
    with _lock:
        if concern not in _clients:
            _clients[concern] = RegistryClient(concern)
        return _clients[concern]
