import hashlib
import json
import sqlite3
from typing import Optional

from fastapi import APIRouter, HTTPException, Path, Query

from training_service.core import db
from training_service.core.config import cfg
from training_service.core.schemas import (
    BatchIngestRequest,
    BatchIngestResponse,
    ErrorResponse,
    GameDetail,
    GameIngest,
    GameIngested,
    GameListResponse,
    LeagueImportRequest,
    LeagueImportResponse,
    PaginatedMeta,
)

router = APIRouter(prefix="/games", tags=["Games"])


def _derive_id(data: dict) -> str:
    for k in ("matchId", "match_id", "gameId", "id"):
        if data.get(k):
            return str(data[k])
    return (
        "game_"
        + hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()[:16]
    )


def _row(r):
    return GameIngested(
        id=r["id"],
        source=r["source"],
        patch=r["patch"],
        match_type=r["match_type"],
        ingested_at=r["ingested_at"],
    )


def _table_exists(conn: sqlite3.Connection, table: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)
    ).fetchone()
    return row is not None


def _columns(conn: sqlite3.Connection, table: str) -> set[str]:
    if not _table_exists(conn, table):
        return set()
    return {r["name"] for r in conn.execute(f"PRAGMA table_info({table})")}


def _first_col(cols: set[str], candidates: tuple[str, ...]) -> str | None:
    return next((c for c in candidates if c in cols), None)


def _fetch_grouped(conn: sqlite3.Connection, table: str, match_id: str) -> dict:
    cols = _columns(conn, table)
    if not cols:
        return {}
    match_col = _first_col(cols, ("match_id", "matchId", "game_id", "gameId"))
    puuid_col = _first_col(cols, ("puuid", "player_id", "summoner_id"))
    if not match_col or not puuid_col:
        return {}
    rows = conn.execute(
        f"SELECT * FROM {table} WHERE {match_col}=?", (match_id,)
    ).fetchall()
    grouped: dict = {}
    for row in rows:
        item = dict(row)
        grouped.setdefault(str(item.get(puuid_col)), []).append(item)
    return grouped


def _league_game_rows(body: LeagueImportRequest):
    conn = sqlite3.connect(cfg.LEAGUE_DB)
    conn.row_factory = sqlite3.Row
    try:
        match_cols = _columns(conn, "matches")
        if not match_cols:
            raise HTTPException(
                status_code=503,
                detail=f"LEAGUE_DB at '{cfg.LEAGUE_DB}' has no matches table.",
            )
        match_id_col = _first_col(match_cols, ("match_id", "matchId", "id", "game_id"))
        if not match_id_col:
            raise HTTPException(
                status_code=503,
                detail="LEAGUE_DB matches table has no match_id/id column.",
            )

        clauses, params = [], []
        match_type_col = _first_col(match_cols, ("match_type", "gameType", "queueType"))
        if body.match_type and match_type_col:
            clauses.append(f"{match_type_col}=?")
            params.append(body.match_type)
        where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
        rows = conn.execute(
            f"SELECT * FROM matches {where} ORDER BY {match_id_col} LIMIT ? OFFSET ?",
            params + [body.limit, body.offset],
        ).fetchall()

        ps_cols = _columns(conn, "player_stats")
        ps_match_col = _first_col(ps_cols, ("match_id", "matchId", "game_id", "gameId"))
        items_by_match = _table_exists(conn, "player_items")
        runes_by_match = _table_exists(conn, "player_runes")

        for match in rows:
            match_data = dict(match)
            match_id = str(match_data[match_id_col])
            participants = []
            if ps_match_col:
                stat_rows = conn.execute(
                    f"SELECT * FROM player_stats WHERE {ps_match_col}=?", (match_id,)
                ).fetchall()
                items = (
                    _fetch_grouped(conn, "player_items", match_id)
                    if items_by_match
                    else {}
                )
                runes = (
                    _fetch_grouped(conn, "player_runes", match_id)
                    if runes_by_match
                    else {}
                )
                for stat in stat_rows:
                    participant = dict(stat)
                    puuid = str(participant.get("puuid"))
                    if puuid in items:
                        participant["items"] = items[puuid]
                    if puuid in runes:
                        participant["runes"] = runes[puuid]
                    participants.append(participant)
            yield match_id, {**match_data, "participants": participants}
    finally:
        conn.close()


@router.post(
    "", response_model=GameIngested, status_code=201, summary="Ingest a single match"
)
def ingest_game(body: GameIngest):
    gid = body.id or _derive_id(body.data)
    db.insert_game(gid, body.data, source=body.source)
    return _row(db.get_game(gid))


@router.post(
    "/batch",
    response_model=BatchIngestResponse,
    status_code=201,
    summary="Ingest up to 1000 matches",
)
def ingest_batch(body: BatchIngestRequest):
    if len(body.games) > 1000:
        raise HTTPException(status_code=422, detail="Batch limit is 1000 games.")
    ingested = skipped = 0
    errors = []
    for item in body.games:
        try:
            gid = item.id or _derive_id(item.data)
            db.insert_game(gid, item.data, source=item.source or body.source)
            ingested += 1
        except Exception as e:
            errors.append({"id": item.id, "error": str(e)})
            skipped += 1
    return BatchIngestResponse(ingested=ingested, skipped=skipped, errors=errors)


@router.post(
    "/import/league",
    response_model=LeagueImportResponse,
    summary="Copy matches from LEAGUE_DB into ml-db",
)
def import_league_games(body: LeagueImportRequest):
    imported = skipped = 0
    errors = []
    try:
        rows = list(_league_game_rows(body))
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=503, detail=f"LEAGUE_DB unavailable: {exc}"
        ) from exc

    for game_id, raw in rows:
        try:
            db.insert_game(game_id, raw, source="league_db")
            imported += 1
        except Exception as exc:
            errors.append({"id": game_id, "error": str(exc)})
            skipped += 1
    return LeagueImportResponse(imported=imported, skipped=skipped, errors=errors)


@router.get("", response_model=GameListResponse, summary="List games")
def list_games(
    source: Optional[str] = None,
    patch: Optional[str] = None,
    match_type: Optional[str] = None,
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
):
    rows, total = db.list_games(
        source=source, patch=patch, match_type=match_type, limit=limit, offset=offset
    )
    return GameListResponse(
        games=[_row(r) for r in rows],
        meta=PaginatedMeta(total=total, limit=limit, offset=offset),
    )


@router.get(
    "/{game_id}",
    response_model=GameDetail,
    summary="Fetch stored match",
    responses={404: {"model": ErrorResponse}},
)
def get_game(game_id: str = Path(...)):
    row = db.get_game(game_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Game '{game_id}' not found.")
    return GameDetail(
        id=row["id"],
        source=row["source"],
        patch=row["patch"],
        match_type=row["match_type"],
        duration_sec=row.get("duration_sec"),
        platform=row.get("platform"),
        ingested_at=row["ingested_at"],
        raw_json=row["raw_json"],
    )


@router.delete(
    "/{game_id}",
    status_code=204,
    summary="Delete a game",
    responses={404: {"model": ErrorResponse}},
)
def delete_game(game_id: str = Path(...)):
    if db.get_game(game_id) is None:
        raise HTTPException(status_code=404, detail=f"Game '{game_id}' not found.")
    db.delete_game(game_id)