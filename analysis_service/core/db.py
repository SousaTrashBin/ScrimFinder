"""
Read-only DB access for the Analysis Service.
Only reads the models table from platform.db to find active model paths.
"""
import json, sqlite3
from contextlib import contextmanager
from typing import Optional
from analysis_service.core.config import cfg

@contextmanager
def get_conn():
    conn = sqlite3.connect(cfg.PLATFORM_DB, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

def get_active_model(concern: str) -> Optional[dict]:
    try:
        with get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM models WHERE concern=? AND is_active=1", (concern,)
            ).fetchone()
        if row is None:
            return None
        d = dict(row)
        for f in ("metrics", "hyperparams", "feature_names"):
            d[f] = json.loads(d[f]) if d[f] else {}
        d["is_active"] = bool(d["is_active"])
        return d
    except Exception:
        return None
