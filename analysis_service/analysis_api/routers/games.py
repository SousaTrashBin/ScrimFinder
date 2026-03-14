"""api/routers/games.py — /games: ingest, list, fetch, delete."""
import hashlib, json
from typing import Optional
from fastapi import APIRouter, HTTPException, Query, Path
from core import db
from analysis_api.core.schemas import (GameIngest,GameIngested,GameDetail,GameListResponse,
    BatchIngestRequest,BatchIngestResponse,PaginatedMeta,ErrorResponse)

router = APIRouter(prefix="/games", tags=["Games"])

def _derive_id(data):
    for k in ("matchId","match_id","gameId","id"):
        if data.get(k): return str(data[k])
    return "game_" + hashlib.sha1(json.dumps(data,sort_keys=True).encode()).hexdigest()[:16]

def _row(r): return GameIngested(id=r["id"],source=r["source"],patch=r["patch"],match_type=r["match_type"],ingested_at=r["ingested_at"])

@router.post("",response_model=GameIngested,status_code=201,summary="Ingest a single match")
def ingest_game(body:GameIngest):
    gid=body.id or _derive_id(body.data)
    db.insert_game(gid,body.data,source=body.source)
    return _row(db.get_game(gid))

@router.post("/batch",response_model=BatchIngestResponse,status_code=201,summary="Ingest up to 1000 matches")
def ingest_batch(body:BatchIngestRequest):
    if len(body.games)>1000: raise HTTPException(422,"Batch limit is 1000 games.")
    ingested=skipped=0; errors=[]
    for item in body.games:
        try:
            gid=item.id or _derive_id(item.data)
            db.insert_game(gid,item.data,source=item.source or body.source)
            ingested+=1
        except Exception as e: errors.append({"id":item.id,"error":str(e)}); skipped+=1
    return BatchIngestResponse(ingested=ingested,skipped=skipped,errors=errors)

@router.get("",response_model=GameListResponse,summary="List games (paginated)")
def list_games(source:Optional[str]=None,patch:Optional[str]=None,
               match_type:Optional[str]=None,limit:int=Query(50,ge=1,le=500),offset:int=Query(0,ge=0)):
    rows,total=db.list_games(source=source,patch=patch,match_type=match_type,limit=limit,offset=offset)
    return GameListResponse(games=[_row(r) for r in rows],meta=PaginatedMeta(total=total,limit=limit,offset=offset))

@router.get("/{game_id}",response_model=GameDetail,summary="Fetch stored match + raw JSON",responses={404:{"model":ErrorResponse}})
def get_game(game_id:str=Path(...)):
    row=db.get_game(game_id)
    if row is None: raise HTTPException(404,f"Game '{game_id}' not found.")
    return GameDetail(id=row["id"],source=row["source"],patch=row["patch"],match_type=row["match_type"],
        duration_sec=row.get("duration_sec"),platform=row.get("platform"),
        ingested_at=row["ingested_at"],raw_json=row["raw_json"])

@router.delete("/{game_id}",status_code=204,summary="Delete a game",responses={404:{"model":ErrorResponse}})
def delete_game(game_id:str=Path(...)):
    if db.get_game(game_id) is None: raise HTTPException(404,f"Game '{game_id}' not found.")
    with db.get_conn() as conn: conn.execute("DELETE FROM games WHERE id=?",(game_id,))
