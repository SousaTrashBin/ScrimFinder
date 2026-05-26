"""
jwt_manager/tests/bq_mock.py

BigQuery mock for JWT Manager unit/acceptance tests.
Mirrors training_service/tests/bq_mock.py but for JWT tables (users, refresh_tokens, access_sessions).
"""

from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from typing import Any, Optional, List, Dict
from unittest.mock import MagicMock


class FakeRow:
    def __init__(self, data: dict):
        self._data = data

    def items(self):
        return self._data.items()

    def __getattr__(self, name: str):
        return self._data.get(name)

    def __getitem__(self, key: str):
        return self._data[key]


class BQMock:
    """In-memory mock for BigQuery JWT tables."""

    TABLES = ["users", "refresh_tokens", "access_sessions"]

    def __init__(self, monkeypatch, project: str = "test-project", platform_dataset: str = "test_platform"):
        self.project = project
        self.platform_dataset = platform_dataset
        self.tables: Dict[str, List[Dict[str, Any]]] = {t: [] for t in self.TABLES}
        self._patch(monkeypatch)

    def clear(self):
        for t in self.TABLES:
            self.tables[t] = []

    def _execute(self, sql: str, params: Optional[List[Any]] = None) -> List[FakeRow]:
        sql = sql.strip()
        params = params or []
        for i, p in enumerate(params):
            sql = sql.replace(f"@p{i}", repr(p) if isinstance(p, str) else str(p))

        sql_upper = sql.upper()

        if sql_upper.startswith("CREATE TABLE"):
            return []
        if sql_upper.startswith("INSERT"):
            return self._handle_insert(sql)
        if sql_upper.startswith("UPDATE"):
            return self._handle_update(sql)
        if sql_upper.startswith("DELETE"):
            return self._handle_delete(sql)
        if sql_upper.startswith("SELECT"):
            return self._handle_select(sql)
        return []

    def _table_from_sql(self, sql: str) -> Optional[str]:
        for t in self.TABLES:
            if f".{t}`" in sql or f".{t} " in sql.upper():
                return t
        return None

    def _handle_insert(self, sql: str) -> List[FakeRow]:
        import re
        table = self._table_from_sql(sql)
        if table is None:
            return []
        cols_match = re.search(r"INSERT\s+INTO\s+`?[^`]+`?\.\s*`?[^`]+`?\.\s*`?([^`]+)`?\s*\(([^)]+)\)", sql, re.IGNORECASE)
        if not cols_match:
            return []
        cols = [c.strip() for c in cols_match.group(2).split(",")]
        vals_match = re.search(r"VALUES\s*\((.+?)\)\s*$", sql, re.DOTALL | re.IGNORECASE)
        if not vals_match:
            return []
        vals_str = vals_match.group(1)
        vals = re.findall(r"(@p\d+|'[^']*'|\d+|TRUE|FALSE|CURRENT_TIMESTAMP\(\)|PARSE_JSON\([^)]+\))", vals_str)

        row = {}
        for col, val in zip(cols, vals):
            val = val.strip()
            if val.startswith("'") and val.endswith("'"):
                val = val[1:-1]
            elif val.startswith("PARSE_JSON('"):
                val = json.loads(val[12:-2])
            elif val == "CURRENT_TIMESTAMP()":
                val = datetime.now(timezone.utc).isoformat()
            elif val in ("TRUE", "true"):
                val = True
            elif val in ("FALSE", "false"):
                val = False
            elif val.isdigit() or (val.startswith("-") and val[1:].isdigit()):
                val = int(val)
            row[col] = val

        self.tables[table].append(row)
        return []

    def _handle_update(self, sql: str) -> List[FakeRow]:
        import re
        table = self._table_from_sql(sql)
        if table is None:
            return []
        set_match = re.search(r"SET\s+(.+?)\s+WHERE", sql, re.DOTALL | re.IGNORECASE)
        if not set_match:
            return []
        sets = {}
        for pair in set_match.group(1).split(","):
            if "=" in pair:
                k, v = pair.split("=", 1)
                sets[k.strip()] = v.strip()

        where_match = re.search(r"WHERE\s+(.+)$", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""

        for r in self.tables[table]:
            if self._matches_where(r, where):
                for k, v in sets.items():
                    if v in ("TRUE", "true"):
                        r[k] = True
                    elif v in ("FALSE", "false"):
                        r[k] = False
                    elif v.isdigit():
                        r[k] = int(v)
                    elif v == "CURRENT_TIMESTAMP()":
                        r[k] = datetime.now(timezone.utc).isoformat()
                    elif v.startswith("'") and v.endswith("'"):
                        r[k] = v[1:-1]
        return []

    def _handle_delete(self, sql: str) -> List[FakeRow]:
        import re
        table = self._table_from_sql(sql)
        if table is None:
            return []
        where_match = re.search(r"WHERE\s+(.+)$", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""
        self.tables[table] = [r for r in self.tables[table] if not self._matches_where(r, where)]
        return []

    def _handle_select(self, sql: str) -> List[FakeRow]:
        import re
        table = self._table_from_sql(sql)
        if table is None:
            return []
        where_match = re.search(r"WHERE\s+(.+?)(?:ORDER\s+BY|LIMIT|OFFSET|$)", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""

        rows = [r for r in self.tables[table] if self._matches_where(r, where)]

        order_match = re.search(r"ORDER\s+BY\s+(\w+)\s*(DESC)?", sql, re.IGNORECASE)
        if order_match:
            col = order_match.group(1)
            desc = bool(order_match.group(2))
            rows.sort(key=lambda r: r.get(col, "") or "", reverse=desc)

        limit_match = re.search(r"LIMIT\s+(\d+)", sql, re.IGNORECASE)
        offset_match = re.search(r"OFFSET\s+(\d+)", sql, re.IGNORECASE)
        limit = int(limit_match.group(1)) if limit_match else len(rows)
        offset = int(offset_match.group(1)) if offset_match else 0
        rows = rows[offset:offset + limit]

        if "COUNT(*)" in sql.upper():
            return [FakeRow({"c": len(rows)})]

        return [FakeRow(r) for r in rows]

    def _matches_where(self, row: dict, where: str) -> bool:
        if not where:
            return True
        import re
        clauses = [c.strip() for c in where.split("AND")]
        for clause in clauses:
            m = re.match(r"(\w+)\s*=\s*'([^']*)'", clause)
            if m:
                col, val = m.group(1), m.group(2)
                if str(row.get(col, "")) != val:
                    return False
                continue
            m = re.match(r"(\w+)\s*=\s*(.+)", clause)
            if m:
                col, val = m.group(1), m.group(2).strip()
                if str(row.get(col, "")) != str(val):
                    return False
                continue
            m = re.match(r"(\w+)\s*=\s*(TRUE|FALSE)", clause, re.IGNORECASE)
            if m:
                col, val = m.group(1), m.group(2).upper() == "TRUE"
                if row.get(col) != val:
                    return False
                continue
        return True

    def _patch(self, monkeypatch):
        import jwt_manager.core.db as db_mod
        import jwt_manager.core.config as cfg_mod

        monkeypatch.setattr(cfg_mod.cfg, "BQ_PROJECT", self.project)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_PLATFORM_DATASET", self.platform_dataset)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_LOCATION", "US")

        monkeypatch.setattr(db_mod, "_bq_query", self._execute)

        fake_client = MagicMock()
        fake_client.project = self.project
        monkeypatch.setattr(db_mod, "get_bq_client", lambda: fake_client)
        monkeypatch.setattr(db_mod, "init_db", lambda: None)
