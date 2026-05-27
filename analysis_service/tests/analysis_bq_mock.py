"""
analysis_service/tests/analysis_bq_mock.py

BigQuery mock for Analysis Service unit/acceptance tests.
"""

from __future__ import annotations

import json
import re
from datetime import datetime, timezone
from typing import Any, Optional, List, Dict
from unittest.mock import MagicMock


class FakeRow:
    def __init__(self, data: dict):
        self._data = data

    def items(self):
        return self._data.items()

    def keys(self):
        return self._data.keys()

    def values(self):
        return self._data.values()

    def get(self, key: str, default=None):
        return self._data.get(key, default)

    def __iter__(self):
        return iter(self._data)

    def __getattr__(self, name: str):
        return self._data.get(name)

    def __getitem__(self, key: str):
        return self._data[key]


class BQMock:
    """In-memory mock for BigQuery analysis tables."""

    TABLES = [
        "matches", "player_stats", "team_stats", "bans", "player_items", "player_runes",
        "dim_champions", "dim_items", "dim_runes", "dim_players", "models"
    ]

    def __init__(self, monkeypatch, project: str = "test-project", dataset: str = "test_league", platform_dataset: str = "test_platform"):
        self.project = project
        self.dataset = dataset
        self.platform_dataset = platform_dataset
        self.tables: Dict[str, List[Dict[str, Any]]] = {t: [] for t in self.TABLES}
        self._patch(monkeypatch)

    def seed(self, table: str, rows: List[dict]):
        self.tables[table] = [dict(r) for r in rows]

    def clear(self):
        for t in self.TABLES:
            self.tables[t] = []

    def _execute(self, sql: str, params: Optional[List[Any]] = None) -> List[FakeRow]:
        sql = sql.strip()
        params = params or []
        for i in sorted(range(len(params)), reverse=True):
            value = self._format_param(params[i])
            sql = sql.replace(f"@p{i}", value)
        for p in params:
            sql = sql.replace("%s", self._format_param(p), 1)

        sql_upper = sql.upper()

        if sql_upper.startswith("CREATE TABLE"):
            return []
        if sql_upper.startswith("MERGE"):
            return self._handle_merge(sql)
        if sql_upper.startswith("INSERT"):
            return self._handle_insert(sql)
        if sql_upper.startswith("UPDATE"):
            return self._handle_update(sql)
        if sql_upper.startswith("DELETE"):
            return self._handle_delete(sql)
        if sql_upper.startswith("SELECT"):
            return self._handle_select(sql)
        return []

    @staticmethod
    def _format_param(value: Any) -> str:
        if value is None:
            return "NULL"
        if isinstance(value, bool):
            return "TRUE" if value else "FALSE"
        if isinstance(value, str):
            return "'" + value.replace("'", "''") + "'"
        return str(value)

    def _table_from_sql(self, sql: str) -> Optional[str]:
        """Extract the primary table from the FROM clause."""
        # Find the table right after FROM (before any JOIN)
        from_match = re.search(r"FROM\s+`[^`]*\.([^`.]+)`(?:\s+(?:AS\s+)?\w+)?", sql, re.IGNORECASE)
        if from_match:
            table_name = from_match.group(1)
            if table_name in self.TABLES:
                return table_name
        from_match = re.search(r"FROM\s+(\w+)(?:\s+(?:AS\s+)?\w+)?", sql, re.IGNORECASE)
        if from_match:
            table_name = from_match.group(1)
            if table_name in self.TABLES:
                return table_name
        # Fallback: find any known table reference
        for t in self.TABLES:
            if f".{t}`" in sql or re.search(rf"\b{re.escape(t)}\b", sql, re.IGNORECASE):
                return t
        return None

    def _handle_merge(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []
        using_match = re.search(r"USING\s*\(SELECT(.+?)\)\s*S", sql, re.DOTALL | re.IGNORECASE)
        if not using_match:
            return []
        using_part = using_match.group(1)
        vals = re.findall(r"(@p\d+|'[^']*'|\d+|TRUE|FALSE|CURRENT_TIMESTAMP\(\)|PARSE_JSON\([^)]+\))", using_part)
        cols = re.findall(r"as\s+(\w+)", using_part, re.IGNORECASE)

        on_match = re.search(r"ON\s+T\.(\w+)\s*=\s*S\.\w+\s*(?:AND\s+T\.(\w+)\s*=\s*S\.\w+)?", sql, re.IGNORECASE)
        on_keys = [k for k in (on_match.group(1), on_match.group(2)) if k] if on_match else ["id"]

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

        existing = None
        for r in self.tables[table]:
            if all(r.get(k) == row.get(k) for k in on_keys):
                existing = r
                break

        if existing:
            update_match = re.search(r"WHEN\s+MATCHED\s+THEN\s+UPDATE\s+SET(.+?)(?:WHEN\s+NOT\s+MATCHED|$)", sql, re.DOTALL | re.IGNORECASE)
            if update_match:
                sets = update_match.group(1)
                set_pairs = re.findall(r"(\w+)\s*=\s*S\.(\w+)", sets)
                for target, source in set_pairs:
                    if source in row:
                        existing[target] = row[source]
        else:
            insert_match = re.search(r"WHEN\s+NOT\s+MATCHED\s+THEN\s+INSERT\s*\(([^)]+)\)\s*VALUES\s*\(([^)]+)\)", sql, re.DOTALL | re.IGNORECASE)
            if insert_match:
                insert_cols = [c.strip() for c in insert_match.group(1).split(",")]
                val_refs = re.findall(r"S\.(\w+)", insert_match.group(2))
                new_row = {c: row.get(c) for c in insert_cols}
                self.tables[table].append(new_row)

        return []

    def _handle_insert(self, sql: str) -> List[FakeRow]:
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
        table = self._table_from_sql(sql)
        if table is None:
            return []
        where_match = re.search(r"WHERE\s+(.+)$", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""
        self.tables[table] = [r for r in self.tables[table] if not self._matches_where(r, where)]
        return []

    def _handle_select(self, sql: str) -> List[FakeRow]:
        table = self._table_from_sql(sql)
        if table is None:
            return []

        # Extract WHERE clause
        where_match = re.search(r"WHERE\s+(.+?)(?:ORDER\s+BY|LIMIT|OFFSET|$)", sql, re.DOTALL | re.IGNORECASE)
        where = where_match.group(1).strip() if where_match else ""

        joined = sql.upper()
        if table == "player_stats" and "JOIN" in joined:
            aggregate = self._handle_player_stats_join_select(sql, where)
            if aggregate is not None:
                return aggregate
        if table == "player_items" and "JOIN" in joined:
            items = self._handle_top_items_select(sql, where)
            if items is not None:
                return items

        # Filter rows
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
        rows = rows[offset:offset + limit]

        # Handle aggregation queries
        select_part = re.search(r"SELECT\s+(.+?)\s+FROM", sql, re.DOTALL | re.IGNORECASE)
        if select_part:
            select_cols = select_part.group(1)
            # Check if this is a pure COUNT(*) query
            if re.match(r"^\s*COUNT\(\*\)\s*$", select_cols, re.IGNORECASE):
                return [FakeRow({"c": len(rows)})]

            # Handle aggregation queries (SUM, AVG, COUNT, ROUND, etc.)
            if any(func in select_cols.upper() for func in ["SUM(", "AVG(", "COUNT(", "ROUND("]):
                return [self._aggregate_row(rows, select_cols)]

        return [FakeRow(r) for r in rows]

    @staticmethod
    def _literal(raw: str) -> str:
        raw = raw.strip()
        if raw.startswith("'") and raw.endswith("'"):
            return raw[1:-1].replace("''", "'")
        return raw

    def _extract_eq(self, where: str, column: str, alias: Optional[str] = None) -> Optional[str]:
        prefixes = [rf"{re.escape(alias)}\."] if alias else [r"(?:\w+\.)?", ""]
        for prefix in prefixes:
            pattern = rf"{prefix}{re.escape(column)}\s*=\s*('[^']*'|\d+|TRUE|FALSE)"
            match = re.search(pattern, where, re.IGNORECASE)
            if match:
                return self._literal(match.group(1))
        return None

    def _match_for_id(self, match_id: Any) -> Optional[dict]:
        for row in self.tables["matches"]:
            if str(row.get("match_id")) == str(match_id):
                return row
        return None

    def _player_stats_rows(self, where: str) -> List[dict]:
        champion_id = self._extract_eq(where, "champion_id", "ps")
        position = self._extract_eq(where, "position", "ps")
        puuid = self._extract_eq(where, "puuid", "ps")
        win = self._extract_eq(where, "win", "ps")
        match_type = self._extract_eq(where, "match_type", "m")

        rows = []
        for row in self.tables["player_stats"]:
            if champion_id is not None and str(row.get("champion_id")) != champion_id:
                continue
            if position is not None and str(row.get("position")) != position:
                continue
            if puuid is not None and str(row.get("puuid")) != puuid:
                continue
            if win is not None and str(int(bool(row.get("win")))) != str(int(win in ("1", "TRUE", "True", True))):
                continue
            if match_type is not None:
                match = self._match_for_id(row.get("match_id"))
                if match is None or str(match.get("match_type")) != match_type:
                    continue
            rows.append(row)
        return rows

    def _handle_player_stats_join_select(self, sql: str, where: str) -> Optional[List[FakeRow]]:
        select_match = re.search(r"SELECT\s+(.+?)\s+FROM", sql, re.DOTALL | re.IGNORECASE)
        if not select_match:
            return None
        select_cols = select_match.group(1)
        select_upper = select_cols.upper()
        rows = self._player_stats_rows(where)

        if "SUM(PS.WIN)" in select_upper and "COUNT(*)" in select_upper:
            wins = sum(int(bool(r.get("win"))) for r in rows)
            return [FakeRow({"wins": wins, "losses": len(rows) - wins, "total": len(rows)})]

        if "AVG(PS.KDA)" in select_upper:
            def avg(column: str) -> float:
                vals = [r.get(column) for r in rows if r.get(column) is not None]
                return sum(vals) / len(vals) if vals else 0.0

            return [
                FakeRow(
                    {
                        "avgKda": round(avg("kda"), 2),
                        "avgDamage": round(avg("dmg_champs"), 0),
                        "avgGold": round(avg("gold"), 0),
                        "avgCs": round(avg("cs"), 1),
                        "avgVisionScore": round(avg("vision"), 1),
                    }
                )
            ]

        if "JOIN DIM_CHAMPIONS" in select_upper or "JOIN `" in sql.upper() and "DIM_CHAMPIONS" in sql.upper():
            if "GROUP BY" in sql.upper():
                grouped: Dict[str, Dict[str, Any]] = {}
                for row in rows:
                    cid = str(row.get("champion_id"))
                    bucket = grouped.setdefault(cid, {"total": 0, "wins": 0})
                    bucket["total"] += 1
                    bucket["wins"] += int(bool(row.get("win")))

                min_games = 101 if re.search(r"HAVING\s+COUNT\(\*\)\s*>\s*100", sql, re.IGNORECASE) else 0
                name_by_id = {str(r.get("id")): r.get("name") for r in self.tables["dim_champions"]}
                result = []
                for cid, stats in grouped.items():
                    if stats["total"] < min_games:
                        continue
                    result.append(
                        {
                            "name": name_by_id.get(cid),
                            "total": stats["total"],
                            "wins": stats["wins"],
                            "win_rate": round(stats["wins"] / max(stats["total"], 1) * 100, 2),
                        }
                    )
                result.sort(key=lambda r: r["win_rate"], reverse=True)
                limit_match = re.search(r"LIMIT\s+(\d+)", sql, re.IGNORECASE)
                limit = int(limit_match.group(1)) if limit_match else len(result)
                return [FakeRow(r) for r in result[:limit]]

        return None

    def _handle_top_items_select(self, sql: str, where: str) -> Optional[List[FakeRow]]:
        if "PLAYER_STATS" not in sql.upper() or "DIM_ITEMS" not in sql.upper():
            return None

        stat_rows = self._player_stats_rows(where)
        players = {(str(r.get("match_id")), str(r.get("puuid"))) for r in stat_rows}
        excluded = {"0", "1001", "2003", "2031", "2055", "3340", "3364", "3363"}
        counts: Dict[str, int] = {}
        for item in self.tables["player_items"]:
            key = (str(item.get("match_id")), str(item.get("puuid")))
            item_id = str(item.get("item_id"))
            if key not in players or item_id in excluded:
                continue
            counts[item_id] = counts.get(item_id, 0) + 1

        name_by_id = {str(r.get("id")): r.get("name") for r in self.tables["dim_items"]}
        rows = [
            {"name": name_by_id.get(item_id, item_id), "cnt": count}
            for item_id, count in counts.items()
        ]
        rows.sort(key=lambda r: r["cnt"], reverse=True)
        limit_match = re.search(r"LIMIT\s+(\d+)", sql, re.IGNORECASE)
        limit = int(limit_match.group(1)) if limit_match else len(rows)
        return [FakeRow(r) for r in rows[:limit]]

    def _get_col(self, row: dict, col_name: str):
        """Get column value case-insensitively."""
        for k, v in row.items():
            if k.lower() == col_name.lower():
                return v
        return None

    def _aggregate_row(self, rows: List[dict], select_cols: str) -> FakeRow:
        """Compute aggregated values from rows."""
        result = {}

        # Split select_cols by comma, but be careful with nested parens
        parts = []
        current = ""
        depth = 0
        for char in select_cols:
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            elif char == "," and depth == 0:
                if current.strip():
                    parts.append(current.strip())
                current = ""
                continue
            current += char
        if current.strip():
            parts.append(current.strip())

        for part in parts:
            # Try to extract alias
            alias_match = re.search(r"AS\s+(\w+)$", part, re.IGNORECASE)
            alias = alias_match.group(1) if alias_match else part.strip()
            # Clean alias from table prefix
            if "." in alias:
                alias = alias.split(".")[-1]

            expr = part
            if alias_match:
                expr = re.sub(r"\s+AS\s+\w+$", "", part, flags=re.IGNORECASE).strip()

            expr_upper = expr.upper()

            if "COUNT(*)" in expr_upper:
                result[alias] = len(rows)
            elif "SUM(" in expr_upper:
                col_match = re.search(r"SUM\(([\w.]+)\)", expr_upper)
                if col_match:
                    col = col_match.group(1).split(".")[-1]
                    result[alias] = sum(self._get_col(r, col) or 0 for r in rows)
                else:
                    result[alias] = len(rows)
            elif "AVG(" in expr_upper:
                col_match = re.search(r"AVG\(([\w.]+)\)", expr_upper)
                if col_match:
                    col = col_match.group(1).split(".")[-1]
                    vals = [self._get_col(r, col) or 0 for r in rows if self._get_col(r, col) is not None]
                    result[alias] = round(sum(vals) / max(len(vals), 1), 2) if vals else 0
                else:
                    result[alias] = 0
            elif "ROUND(" in expr_upper:
                inner_match = re.search(r"ROUND\((.+?)\s*,\s*(\d+)\)", expr, re.IGNORECASE)
                if inner_match:
                    inner_expr, decimals = inner_match.group(1), int(inner_match.group(2))
                    if "AVG(" in inner_expr.upper():
                        col_match = re.search(r"AVG\(([\w.]+)\)", inner_expr.upper())
                        if col_match:
                            col = col_match.group(1).split(".")[-1]
                            vals = [self._get_col(r, col) or 0 for r in rows if self._get_col(r, col) is not None]
                            avg_val = sum(vals) / max(len(vals), 1) if vals else 0
                            result[alias] = round(avg_val, decimals)
                        else:
                            result[alias] = 0
                    else:
                        result[alias] = 0
                else:
                    result[alias] = 0
            else:
                # Simple column reference (possibly with table alias)
                col = expr.split(".")[-1].strip()
                if rows:
                    result[alias] = self._get_col(rows[0], col)
                else:
                    result[alias] = None

        return FakeRow(result)

    def _matches_where(self, row: dict, where: str) -> bool:
        if not where:
            return True

        # Split by AND, but be careful with nested parentheses
        clauses = []
        current = ""
        depth = 0
        i = 0
        while i < len(where):
            char = where[i]
            if char == "(":
                depth += 1
                current += char
            elif char == ")":
                depth -= 1
                current += char
            elif depth == 0 and where[i:i+4].upper() == "AND ":
                if current.strip():
                    clauses.append(current.strip())
                current = ""
                i += 4
                continue
            else:
                current += char
            i += 1
        if current.strip():
            clauses.append(current.strip())

        for clause in clauses:
            clause = clause.strip()
            if not clause:
                continue

            # Handle LOWER(col) = LOWER(val)
            m = re.match(r"LOWER\((\w+)\)\s*=\s*LOWER\('([^']*)'\)", clause, re.IGNORECASE)
            if m:
                col, val = m.group(1), m.group(2).lower()
                if str(row.get(col, "")).lower() != val:
                    return False
                continue

            # Handle col = 'val'
            m = re.match(r"(\w+)\s*=\s*'([^']*)'", clause)
            if m:
                col, val = m.group(1), m.group(2)
                if str(row.get(col, "")) != val:
                    return False
                continue

            # Handle alias.col = 'val' (e.g., ps.champion_id = '22')
            m = re.match(r"(\w+)\.(\w+)\s*=\s*'([^']*)'", clause)
            if m:
                _, col, val = m.group(1), m.group(2), m.group(3)
                if str(row.get(col, "")) != val:
                    return False
                continue

            # Handle alias.col = numeric
            m = re.match(r"(\w+)\.(\w+)\s*=\s*(\d+)", clause)
            if m:
                _, col, val = m.group(1), m.group(2), int(m.group(3))
                if row.get(col) != val and str(row.get(col, "")) != str(val):
                    return False
                continue

            # Handle col = numeric
            m = re.match(r"(\w+)\s*=\s*(\d+)", clause)
            if m:
                col, val = m.group(1), int(m.group(2))
                if row.get(col) != val and str(row.get(col, "")) != str(val):
                    return False
                continue

            # Handle col = TRUE/FALSE
            m = re.match(r"(\w+)\s*=\s*(TRUE|FALSE)", clause, re.IGNORECASE)
            if m:
                col, val = m.group(1), m.group(2).upper() == "TRUE"
                row_val = row.get(col)
                if isinstance(row_val, bool):
                    if row_val != val:
                        return False
                elif isinstance(row_val, str):
                    if (row_val.upper() == "TRUE") != val:
                        return False
                else:
                    return False
                continue

            # Handle alias.col = TRUE/FALSE
            m = re.match(r"(\w+)\.(\w+)\s*=\s*(TRUE|FALSE)", clause, re.IGNORECASE)
            if m:
                _, col, val = m.group(1), m.group(2), m.group(3).upper() == "TRUE"
                row_val = row.get(col)
                if isinstance(row_val, bool):
                    if row_val != val:
                        return False
                elif isinstance(row_val, str):
                    if (row_val.upper() == "TRUE") != val:
                        return False
                else:
                    return False
                continue

            # Handle col != val
            m = re.match(r"(\w+)\s*!=\s*'([^']*)'", clause)
            if m:
                col, val = m.group(1), m.group(2)
                if str(row.get(col, "")) == val:
                    return False
                continue

            # Handle IN clause - conservative: allow all
            if "IN (" in clause.upper():
                continue

            # Handle LIKE clause - conservative: allow all
            if "LIKE" in clause.upper():
                continue

            # Unknown clause - be conservative and reject
            return False

        return True

    def _patch(self, monkeypatch):
        import analysis_service.core.db as db_mod
        import analysis_service.core.config as cfg_mod

        monkeypatch.setattr(cfg_mod.cfg, "BQ_PROJECT", self.project)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_DATASET", self.dataset)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_PLATFORM_DATASET", self.platform_dataset)
        monkeypatch.setattr(cfg_mod.cfg, "BQ_LOCATION", "US")

        monkeypatch.setattr(db_mod, "_bq_query", self._execute)

        fake_client = MagicMock()
        fake_client.project = self.project
        monkeypatch.setattr(db_mod, "get_bq_client", lambda: fake_client)
