import hashlib
import json
import sqlite3
from typing import Optional

from fastapi import APIRouter, HTTPException, Path, Query
from pydantic import BaseModel

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


def _row(r) -> GameIngested:
    return GameIngested(
        id=r["id"],
        source=r["source"],
        patch=r["patch"],
        match_type=r["match_type"],
        ingested_at=str(r["ingested_at"]),
    )


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
        ingested_at=str(row["ingested_at"]),
        raw_json=row["raw_json"],
    )


@router.delete(
    "/{game_id}",
    status_code=204,
    summary="Delete a game",
    responses={404: {"model": ErrorResponse}},
)
def delete_game(game_id: str = Path(...)):
    if not db.delete_game(game_id):
        raise HTTPException(status_code=404, detail=f"Game '{game_id}' not found.")


# ── League DB import ──────────────────────────────────────────────────────────


class LeagueImportRequest(BaseModel):
    limit: Optional[int] = None
    offset: int = 0
    match_type: Optional[str] = None


class LeagueImportResponse(BaseModel):
    imported: int
    skipped: int
    errors: list


@router.post(
    "/import/league",
    response_model=LeagueImportResponse,
    summary="Import matches from the read-only league SQLite dataset",
)
def import_league(body: LeagueImportRequest):
    """
    Reads matches from league_data.db (read-only SQLite import source), assembles each
    match with participants, items, and runes, then stores both raw and normalized
    rows in the deployed ML PostgreSQL database.
    """
    league_path = cfg.LEAGUE_DB
    try:
        conn = sqlite3.connect(f"file:{league_path}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
    except sqlite3.OperationalError as e:
        raise HTTPException(status_code=503, detail=f"League DB unavailable: {e}")

    try:
        for table in ("dim_champions", "dim_items", "dim_runes"):
            try:
                rows = [
                    dict(r) for r in conn.execute(f"SELECT * FROM {table}").fetchall()
                ]
                db.upsert_dimension_rows(table, rows)
            except sqlite3.OperationalError:
                pass

        q = "SELECT * FROM matches"
        params: list = []
        if body.match_type:
            q += " WHERE match_type=?"
            params.append(body.match_type)
        q += " ORDER BY match_id"
        if body.limit is not None:
            q += f" LIMIT {int(body.limit)}"
        if body.offset:
            q += f" OFFSET {int(body.offset)}"

        matches = conn.execute(q, params).fetchall()
        imported = skipped = 0
        errors: list = []

        for m in matches:
            match_id = m["match_id"]
            try:
                participants = []
                for p in conn.execute(
                    "SELECT * FROM player_stats WHERE match_id=?", (match_id,)
                ).fetchall():
                    pdict = dict(p)
                    pdict["items"] = [
                        dict(i)
                        for i in conn.execute(
                            "SELECT * FROM player_items WHERE match_id=? AND puuid=?",
                            (match_id, pdict["puuid"]),
                        ).fetchall()
                    ]
                    pdict["runes"] = [
                        dict(r)
                        for r in conn.execute(
                            "SELECT * FROM player_runes WHERE match_id=? AND puuid=?",
                            (match_id, pdict["puuid"]),
                        ).fetchall()
                    ]
                    participants.append(pdict)

                raw = dict(m)
                raw["participants"] = participants
                db.upsert_league_match(raw)
                db.insert_game(match_id, raw, source="league_db")
                imported += 1
            except Exception as e:
                errors.append({"id": match_id, "error": str(e)})
                skipped += 1

        return LeagueImportResponse(imported=imported, skipped=skipped, errors=errors)
    finally:
        conn.close()
