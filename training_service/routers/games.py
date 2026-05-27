import hashlib
import json
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
    metadata = data.get("metadata")
    if isinstance(metadata, dict):
        for k in ("matchId", "match_id"):
            if metadata.get(k):
                return str(metadata[k])
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


@router.get("", response_model=GameListResponse, summary="List games or get one by ID")
def list_or_get_game(
    game_id: Optional[str] = Query(None, description="Specific game ID to fetch"),
    source: Optional[str] = Query(None, description="Filter by source"),
    patch: Optional[str] = Query(None, description="Filter by patch"),
    match_type: Optional[str] = Query(None, description="Filter by match type"),
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
):
    """
    List games with optional filtering, or fetch a single game by ID.

    - If `game_id` is provided, returns that specific game (404 if not found).
    - Otherwise returns a paginated, filtered list.
    """
    if game_id:
        row = db.get_game(game_id)
        if row is None:
            raise HTTPException(status_code=404, detail=f"Game '{game_id}' not found.")
        return GameListResponse(
            games=[_row(row)],
            meta=PaginatedMeta(total=1, limit=limit, offset=offset),
        )

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
    summary="Fetch stored match full detail",
    responses={404: {"model": ErrorResponse}},
)
def get_game_detail(game_id: str = Path(...)):
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
    summary="Import matches from the read-only league BigQuery dataset",
)
def import_league(body: LeagueImportRequest):
    """
    Reads matches from BigQuery, assembles each match with participants,
    items, and runes, then stores both raw and normalized rows in the BigQuery platform dataset.
    """
    try:
        # Import dimensions first
        for table in ("dim_champions", "dim_items", "dim_runes"):
            try:
                sql = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.{table}`"
                rows = [dict(r) for r in db._bq_query(sql)]
                db.upsert_dimension_rows(table, rows)
            except Exception:
                pass

        # Build matches query
        q = f"SELECT * FROM `{cfg.BQ_PROJECT}.{cfg.BQ_DATASET}.matches`"
        where_clauses = []
        params = []
        if body.match_type:
            where_clauses.append("match_type = @p0")
            params.append(body.match_type)

        if where_clauses:
            q += " WHERE " + " AND ".join(where_clauses)

        q += " ORDER BY match_id"
        if body.limit is not None:
            q += f" LIMIT {int(body.limit)}"
        if body.offset:
            q += f" OFFSET {int(body.offset)}"

        matches = [dict(r) for r in db._bq_query(q, params)]
        imported = skipped = 0
        errors = []

        for m in matches:
            match_id = m["match_id"]
            try:
                # Fetch related data using db helpers
                p_stats = db.get_player_stats(match_id)
                if not p_stats:
                    raise ValueError("No participant stats found")
                p_items = db.get_player_items(match_id)
                p_runes = db.get_player_runes(match_id)

                # Group items/runes by player
                items_by_puuid = {}
                for item in p_items:
                    puuid = item["puuid"]
                    if puuid not in items_by_puuid:
                        items_by_puuid[puuid] = []
                    items_by_puuid[puuid].append(item)

                runes_by_puuid = {}
                for rune in p_runes:
                    puuid = rune["puuid"]
                    if puuid not in runes_by_puuid:
                        runes_by_puuid[puuid] = []
                    runes_by_puuid[puuid].append(rune)

                # Assemble participants
                participants = []
                for p in p_stats:
                    pdict = dict(p)
                    pdict["items"] = items_by_puuid.get(pdict["puuid"], [])
                    pdict["runes"] = runes_by_puuid.get(pdict["puuid"], [])
                    participants.append(pdict)

                raw = dict(m)
                raw["participants"] = participants
                # Also fetch team stats and bans for a complete raw record
                raw["teams"] = db.get_team_stats(match_id)
                raw["bans"] = db.get_bans(match_id)

                # Store in platform BigQuery
                db.upsert_league_match(raw)
                db.insert_game(match_id, raw, source="league_db")
                imported += 1
            except Exception as e:
                errors.append({"id": match_id, "error": str(e)})
                skipped += 1

        return LeagueImportResponse(imported=imported, skipped=skipped, errors=errors)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
