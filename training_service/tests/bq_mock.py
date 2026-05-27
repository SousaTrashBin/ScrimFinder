"""
training_service/tests/bq_mock.py

BigQuery mock for unit and acceptance tests.
Replaces live BQ calls with in-memory dictionaries that mimic Row objects.
"""

from __future__ import annotations

import json
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from unittest.mock import MagicMock


class FakeRow:
    """Mimics a google.cloud.bigquery.Row."""

    def __init__(self, data: dict):
        self._data = data

    def items(self):
        return self._data.items()

    def keys(self):
        return self._data.keys()

    def values(self):
        return self._data.values()

    def __iter__(self):
        return iter(self._data)

    def __getattr__(self, name: str):
        return self._data.get(name)

    def __getitem__(self, key: str):
        return self._data[key]

    def __repr__(self):
        return f"FakeRow({self._data})"

    def get(self, key: str, default=None):
        return self._data.get(key, default)


class BQMock:
    """
    In-memory mock for BigQuery platform tables.

    Usage in tests:
        from training_service.tests.bq_mock import BQMock
        mock = BQMock(monkeypatch)
        mock.seed_games([{"id": "G1", "source": "test", ...}])
        # all db.* calls now hit mock.tables
    """

    TABLES = ["games", "features", "models", "training_jobs", "datasets"]

    def __init__(
        self,
        monkeypatch,
        project: str = "test-project",
        platform_dataset: str = "test_platform",
    ):
        self.project = project
        self.platform_dataset = platform_dataset
        self.tables: Dict[str, List[Dict[str, Any]]] = {t: [] for t in self.TABLES}
        self._patch(monkeypatch)

    # -- Seeding helpers --------------------------------------

    def seed_games(self, rows: List[dict]):
        self.tables["games"] = [dict(r) for r in rows]

    def seed_features(self, rows: List[dict]):
        self.tables["features"] = [dict(r) for r in rows]

    def seed_models(self, rows: List[dict]):
        self.tables["models"] = [dict(r) for r in rows]

    def seed_jobs(self, rows: List[dict]):
        self.tables["training_jobs"] = [dict(r) for r in rows]

    def seed_datasets(self, rows: List[dict]):
        self.tables["datasets"] = [dict(r) for r in rows]

    def seed(self, table: str, rows: List[dict]):
        """Generic seed for any table (used by analysis service too)."""
        if table not in self.tables:
            self.tables[table] = []
        self.tables[table] = [dict(r) for r in rows]

    def clear(self):
        for t in self.TABLES:
            self.tables[t] = []

    # -- Internal query engine --------------------------------

    def _execute(self, sql: str, params: Optional[List[Any]] = None) -> List[FakeRow]:
        """
        Very naive SQL parser -- enough for MERGE/SELECT/INSERT/UPDATE/DELETE
        used by db.py.  Real BQ SQL is much richer; we handle the subset our
        app actually generates.
        """
        sql = sql.strip()
        params = params or []
        # Replace @pN placeholders with actual values -- sort descending so @p10
        # is not mangled by @p1.
        for i in sorted(range(len(params)), reverse=True):
            p = params[i]
            sql = sql.replace(f"@p{i}", self._format_param(p))

        sql_upper = sql.upper()

        # -- MERGE (upsert) -----------------------------------
        if sql_upper.startswith("MERGE"):
            return self._handle_merge(sql)

        # -- INSERT -------------------------------------------
        if sql_upper.startswith("INSERT"):
            return self._handle_insert(sql)

        # -- UPDATE -------------------------------------------
        if sql_upper.startswith("UPDATE"):
            return self._handle_update(sql)

        # -- DELETE -------------------------------------------
        if sql_upper.startswith("DELETE"):
            return self._handle_delete(sql)

        # -- SELECT -------------------------------------------
        if sql_upper.startswith("SELECT"):
            return self._handle_select(sql)

        return []

    @staticmethod
    def _format_param(value: Any) -> str:
        """Render a Python value as the tiny SQL-literal subset this mock parses."""
        if value is None:
            return "NULL"
        if isinstance(value, bool):
            return "TRUE" if value else "FALSE"
        if isinstance(value, str):
            return "'" + value.replace("'", "''") + "'"
        return str(value)

    def _table_from_sql(self, sql: str) -> Optional[str]:
        """Extract table name from a fully-qualified reference."""
        for t in self.tables:
            # Match `project.dataset.table` or `project.dataset.table `
            if f".{t}`" in sql or re.search(rf"\.{re.escape(t)}(?:\s|$)", sql):
                return t
        return None

    @staticmethod
    def _split_exprs(s: str) -> List[str]:
        """Split a comma-separated list respecting nested parentheses."""
        parts = []
        depth = 0
        current = []
        for ch in s:
            if ch == "(":
                depth += 1
                current.append(ch)
            elif ch == ")":
                depth -= 1
                current.append(ch)
            elif ch == "," and depth == 0:
                parts.append("".join(current).strip())
                current = []
            else:
                current.append(ch)
        if current:
            parts.append("".join(current).strip())
        return parts

    @staticmethod
    def _split_select_exprs(s: str) -> List[str]:
        """Split SELECT expressions by comma, respecting nested parens and quoted strings."""
        parts = []
        depth = 0
        in_str = False
        str_char = None
        current = []
        i = 0
        while i < len(s):
            ch = s[i]
            if not in_str and ch in "'\"":
                in_str = True
                str_char = ch
                current.append(ch)
            elif in_str:
                current.append(ch)
                if ch == str_char:
                    # Check for escaped quote (SQL style: '' or "")
                    if i + 1 < len(s) and s[i + 1] == str_char:
                        current.append(s[i + 1])
                        i += 1
                    else:
                        in_str = False
                        str_char = None
            elif ch == "(":
                depth += 1
                current.append(ch)
            elif ch == ")":
                depth -= 1
                current.append(ch)
            elif ch == "," and depth == 0:
                parts.append("".join(current).strip())
                current = []
            else:
                current.append(ch)
            i += 1
        if current:
            parts.append("".join(current).strip())
        return parts

    def _eval_expr(self, expr: str, source_row: dict) -> Any:
        """Evaluate a SQL expression used in MERGE/INSERT/UPDATE."""
        expr = expr.strip()
        # COALESCE(S.col, T.col) -> return S.col value
        m = re.match(r"COALESCE\((S\.\w+),\s*T\.\w+\)", expr, re.IGNORECASE)
        if m:
            return self._eval_expr(m.group(1), source_row)
        # S.column reference
        m = re.match(r"S\.(\w+)$", expr, re.IGNORECASE)
        if m:
            return source_row.get(m.group(1))
        # PARSE_JSON('...')  -- handles both dict and list JSON, and nested quotes
        m = re.match(r"PARSE_JSON\((.+)\)$", expr, re.IGNORECASE | re.DOTALL)
        if m:
            inner = m.group(1).strip()
            # The inner may be a string literal like '{"a": 1}' or a raw expression
            if inner.startswith("'") and inner.endswith("'"):
                inner = inner[1:-1]
            # Un-escape SQL single quotes ('' -> ')
            inner = inner.replace("''", "'")
            try:
                return json.loads(inner)
            except json.JSONDecodeError:
                return inner
        # CURRENT_TIMESTAMP()
        if expr.upper() == "CURRENT_TIMESTAMP()":
            return datetime.now(timezone.utc).isoformat()
        # Booleans
        if expr.upper() == "TRUE":
            return True
        if expr.upper() == "FALSE":
            return False
        # String literal
        if expr.startswith("'") and expr.endswith("'"):
            return expr[1:-1].replace("''", "'")
        # NULL / NONE
        if expr.upper() in ("NULL", "NONE"):
            return None
        # Integer
        if expr.isdigit() or (expr.startswith("-") and expr[1:].isdigit()):
            return int(expr)
        # Float
        try:
            return float(expr)
        except ValueError:
            pass
        return expr

    def _handle_merge(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []

        # -- Build source row from USING clause ---------------
        using_match = re.search(
            r"USING\s*\(\s*SELECT\s+(.+?)\)\s*S", sql, re.DOTALL | re.IGNORECASE
        )
        source_row = {}
        if using_match:
            using_part = using_match.group(1)
            # Use robust parser that handles quoted strings with commas
            parts = self._split_select_exprs(using_part)
            for part in parts:
                m = re.match(r"(.+?)\s+as\s+(\w+)", part.strip(), re.IGNORECASE)
                if m:
                    val_expr = m.group(1).strip()
                    col_name = m.group(2)
                    source_row[col_name] = self._eval_expr(val_expr, {})

        # -- Determine ON keys --------------------------------
        # Find the ON clause, stopping at WHEN MATCHED or WHEN NOT MATCHED
        on_match = re.search(
            r"ON\s+(.+?)(?:WHEN\s+(?:MATCHED|NOT\s+MATCHED))",
            sql,
            re.DOTALL | re.IGNORECASE,
        )
        on_keys = []
        if on_match:
            on_clause = on_match.group(1)
            for m in re.finditer(r"T\.(\w+)\s*=\s*S\.\w+", on_clause, re.IGNORECASE):
                on_keys.append(m.group(1))
        if not on_keys:
            on_keys = list(source_row.keys())

        # -- Find existing row --------------------------------
        existing = None
        for r in self.tables[table]:
            if all(str(r.get(k)) == str(source_row.get(k)) for k in on_keys):
                existing = r
                break

        if existing:
            # UPDATE matched row
            update_match = re.search(
                r"WHEN\s+MATCHED\s+THEN\s+UPDATE\s+SET\s+(.+?)(?:WHEN\s+NOT\s+MATCHED|$)",
                sql,
                re.DOTALL | re.IGNORECASE,
            )
            if update_match:
                for set_expr in self._split_exprs(update_match.group(1)):
                    if "=" in set_expr:
                        k, v = set_expr.split("=", 1)
                        k = k.strip()
                        v = v.strip()
                        existing[k] = self._eval_expr(v, source_row)
        else:
            # INSERT new row
            insert_match = re.search(
                r"WHEN\s+NOT\s+MATCHED\s+THEN\s+INSERT", sql, re.IGNORECASE
            )
            if insert_match:
                cols_match = re.search(
                    r"INSERT\s*\(([^)]+)\)",
                    sql[insert_match.start() :],
                    re.IGNORECASE,
                )
                val_pos = sql.find("VALUES", insert_match.start())
                if val_pos != -1:
                    open_idx = sql.find("(", val_pos)
                    if open_idx != -1:
                        depth = 1
                        close_idx = open_idx + 1
                        while close_idx < len(sql) and depth > 0:
                            if sql[close_idx] == "(":
                                depth += 1
                            elif sql[close_idx] == ")":
                                depth -= 1
                            close_idx += 1
                        vals_str = sql[open_idx + 1 : close_idx - 1]
                        vals = self._split_exprs(vals_str)
                        insert_cols = []
                        if cols_match:
                            insert_cols = [
                                c.strip()
                                for c in self._split_exprs(cols_match.group(1))
                            ]
                        new_row = {}
                        for col, val in zip(insert_cols, vals):
                            new_row[col] = self._eval_expr(val, source_row)
                        self.tables[table].append(new_row)
        return []

    def _handle_insert(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []
        cols_match = re.search(
            r"INSERT\s+INTO\s+`?[^`]+`?\.\s*`?[^`]+`?\.\s*`?([^`]+)`?\s*\(([^)]+)\)",
            sql,
            re.IGNORECASE,
        )
        if not cols_match:
            return []
        cols = [c.strip() for c in self._split_exprs(cols_match.group(2))]

        val_idx = sql.upper().find("VALUES")
        if val_idx == -1:
            return []
        open_idx = sql.find("(", val_idx)
        if open_idx == -1:
            return []
        depth = 1
        close_idx = open_idx + 1
        while close_idx < len(sql) and depth > 0:
            if sql[close_idx] == "(":
                depth += 1
            elif sql[close_idx] == ")":
                depth -= 1
            close_idx += 1
        vals_str = sql[open_idx + 1 : close_idx - 1]
        vals = self._split_exprs(vals_str)

        row = {}
        for col, val in zip(cols, vals):
            row[col] = self._eval_expr(val, {})
        self.tables[table].append(row)
        return []

    def _handle_update(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []
        set_match = re.search(r"SET\s+(.+?)\s+WHERE", sql, re.DOTALL | re.IGNORECASE)
        if not set_match:
            return []
        sets = {}
        for set_expr in self._split_exprs(set_match.group(1)):
            if "=" in set_expr:
                k, v = set_expr.split("=", 1)
                sets[k.strip()] = v.strip()

        where_match = re.search(r"WHERE\s+(.+)$", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""

        for r in self.tables[table]:
            if self._matches_where(r, where):
                for k, v in sets.items():
                    r[k] = self._eval_expr(v, {})
        return []

    def _handle_delete(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []
        where_match = re.search(r"WHERE\s+(.+)$", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""
        self.tables[table] = [
            r for r in self.tables[table] if not self._matches_where(r, where)
        ]
        return []

    def _handle_select(self, sql: str) -> List[FakeRow]:
        # Try to identify the primary table for filtering
        table = self._table_from_sql(sql)
        if table is None:
            for t in self.TABLES:
                if f"`{t}`" in sql or f" {t} " in sql.upper():
                    table = t
                    break

        # Also check for league tables
        if table is None:
            league_tables = [
                "matches",
                "player_stats",
                "team_stats",
                "bans",
                "player_items",
                "player_runes",
                "dim_champions",
                "dim_items",
                "dim_runes",
                "dim_players",
            ]
            for t in league_tables:
                if f".{t}`" in sql or f".{t} " in sql.upper():
                    table = t
                    break

        if table is None:
            return []

        where_match = re.search(
            r"WHERE\s+(.+?)(?:ORDER\s+BY|LIMIT|OFFSET|$)",
            sql,
            re.DOTALL | re.IGNORECASE,
        )
        where = where_match.group(1).strip() if where_match else ""

        rows = [r for r in self.tables[table] if self._matches_where(r, where)]

        # ORDER BY
        order_match = re.search(r"ORDER\s+BY\s+(\w+)\s*(DESC)?", sql, re.IGNORECASE)
        if order_match:
            col = order_match.group(1)
            desc = bool(order_match.group(2))
            rows.sort(key=lambda r: r.get(col, "") or "", reverse=desc)

        # LIMIT / OFFSET
        limit_match = re.search(r"LIMIT\s+(\d+)", sql, re.IGNORECASE)
        offset_match = re.search(r"OFFSET\s+(\d+)", sql, re.IGNORECASE)
        limit = int(limit_match.group(1)) if limit_match else len(rows)
        offset = int(offset_match.group(1)) if offset_match else 0
        rows = rows[offset : offset + limit]

        # COUNT(*) special case
        if "COUNT(*)" in sql.upper():
            return [FakeRow({"c": len(rows)})]

        return [FakeRow(r) for r in rows]

    def _matches_where(self, row: dict, where: str) -> bool:
        """Naive WHERE evaluator for simple equality conditions."""
        if not where:
            return True
        # Split on AND but not inside string literals
        clauses = []
        current = []
        in_str = False
        for ch in where:
            if ch == "'":
                in_str = not in_str
                current.append(ch)
            elif ch.upper() == "A" and not in_str and len(current) >= 2:
                tail = "".join(current[-2:]).upper()
                if (
                    tail == " A"
                    and len(where) > len(current)
                    and where[len(current)].upper() == "N"
                ):
                    # Check next char is D and then space/end
                    if (
                        len(where) > len(current) + 1
                        and where[len(current) + 1].upper() == "D"
                    ):
                        if (
                            len(where) > len(current) + 2
                            and where[len(current) + 2].isspace()
                        ):
                            clauses.append("".join(current[:-2]).strip())
                            current = []
                            continue
                current.append(ch)
            else:
                current.append(ch)
        if current:
            clauses.append("".join(current).strip())

        for clause in clauses:
            clause = clause.strip()
            if not clause:
                continue
            # is_active = TRUE / FALSE
            m = re.match(r"(\w+)\s*=\s*(TRUE|FALSE)", clause, re.IGNORECASE)
            if m:
                col, val = m.group(1), m.group(2).upper() == "TRUE"
                if row.get(col) != val:
                    return False
                continue

            # col IN ('a', 'b', ...)
            m = re.match(r"(\w+)\s+IN\s*\(([^)]+)\)", clause, re.IGNORECASE)
            if m:
                col, vals_str = m.group(1), m.group(2)
                vals = [v.strip().strip("'") for v in vals_str.split(",")]
                if str(row.get(col, "")) not in vals:
                    return False
                continue

            # id = 'value'
            m = re.match(r"(\w+)\s*=\s*\'([^\']*)\'", clause)
            if m:
                col, val = m.group(1), m.group(2)
                if str(row.get(col, "")) != val:
                    return False
                continue

            # id = numeric or already-substituted @pN
            m = re.match(r"(\w+)\s*=\s*(.+)", clause)
            if m:
                col, val = m.group(1), m.group(2).strip()
                if str(row.get(col, "")) != str(val):
                    return False
                continue

            # LOWER(col) = LOWER('val')
            m = re.match(
                r"LOWER\((\w+)\)\s*=\s*LOWER\(\'([^\']*)\'\)", clause, re.IGNORECASE
            )
            if m:
                col, val = m.group(1), m.group(2).lower()
                if str(row.get(col, "")).lower() != val:
                    return False
                continue

            # LOWER(col) = 'val'
            m = re.match(r"LOWER\((\w+)\)\s*=\s*\'([^\']*)\'", clause, re.IGNORECASE)
            if m:
                col, val = m.group(1), m.group(2).lower()
                if str(row.get(col, "")).lower() != val:
                    return False
                continue
        return True

    # -- Patching ---------------------------------------------

    def _patch(self, monkeypatch):
        """Monkey-patch db._bq_query and db.get_bq_client."""
        import training_service.core.db as db_mod
        import training_service.core.config as cfg_mod

        # Patch config so BQ_PROJECT is always set
        monkeypatch.setattr(cfg_mod.cfg, "BQ_PROJECT", self.project)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_PLATFORM_DATASET", self.platform_dataset)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_DATASET", "test_league")
        monkeypatch.setattr(cfg_mod.cfg, "BQ_LOCATION", "US")

        # Patch _bq_query
        monkeypatch.setattr(db_mod, "_bq_query", self._execute)

        # Patch get_bq_client to return a dummy client
        fake_client = MagicMock()
        fake_client.project = self.project
        monkeypatch.setattr(db_mod, "get_bq_client", lambda: fake_client)

        # Patch init_db to no-op (schema creation not needed in memory)
        monkeypatch.setattr(db_mod, "init_db", lambda: None)
        monkeypatch.setattr(db_mod, "init_bq_platform", lambda *a, **k: None)
