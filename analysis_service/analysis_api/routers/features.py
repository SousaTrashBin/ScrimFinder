"""api/routers/features.py — /features: extract and retrieve feature vectors."""
from typing import Optional
from fastapi import APIRouter, HTTPException, Path, Query
from core import db
from analysis_api.core.schemas import (FeatureExtractRequest,FeatureExtractResponse,FeatureVector,ErrorResponse,Concern)
from analysis_api.core.db import now_iso

router = APIRouter(prefix="/features", tags=["Features"])

@router.post("/extract",response_model=FeatureExtractResponse,
    summary="Extract feature vectors",
    description="Extract features for a stored game or inline JSON.\n\n> **Stub:** Returns empty vectors until ingestion/feature_extractor.py is built.",
    responses={404:{"model":ErrorResponse}})
def extract_features(body:FeatureExtractRequest):
    if body.game_id:
        row=db.get_game(body.game_id)
        if row is None: raise HTTPException(404,f"Game '{body.game_id}' not found.")
        game_id,raw=body.game_id,row["raw_json"]
    elif body.raw_data:
        from routers.games import _derive_id
        game_id,raw=_derive_id(body.raw_data),body.raw_data
    else:
        raise HTTPException(422,"Provide either game_id or raw_data.")

    # TODO: from ingestion.feature_extractor import extract; result=extract(raw,[c.value for c in body.concerns])
    vectors=[]
    for concern in body.concerns:
        if body.store and body.game_id:
            db.upsert_features(game_id,concern.value,[],[])
        cached=db.get_features(game_id,concern.value)
        if cached:
            e=cached[0]
            vectors.append(FeatureVector(game_id=game_id,concern=concern.value,
                feature_vector=e["feature_vector"],feature_names=e["feature_names"],
                schema_version=e["schema_version"],extracted_at=e["extracted_at"]))
        else:
            vectors.append(FeatureVector(game_id=game_id,concern=concern.value,
                feature_vector=[],feature_names=[],schema_version="1",extracted_at=now_iso()))
    return FeatureExtractResponse(game_id=game_id,features=vectors,stored=body.store and body.game_id is not None)

@router.get("/{game_id}",response_model=list[FeatureVector],
    summary="Retrieve cached feature vectors",responses={404:{"model":ErrorResponse}})
def get_features(game_id:str=Path(...),concern:Optional[Concern]=Query(None)):
    if db.get_game(game_id) is None: raise HTTPException(404,f"Game '{game_id}' not found.")
    rows=db.get_features(game_id,concern.value if concern else None)
    if not rows: raise HTTPException(404,f"No features for '{game_id}'. Run POST /features/extract first.")
    return [FeatureVector(game_id=r["game_id"],concern=r["concern"],feature_vector=r["feature_vector"],
        feature_names=r["feature_names"],schema_version=r["schema_version"],extracted_at=r["extracted_at"]) for r in rows]

@router.delete("/{game_id}",status_code=204,summary="Delete cached features",responses={404:{"model":ErrorResponse}})
def delete_features(game_id:str=Path(...),concern:Optional[Concern]=Query(None)):
    if db.get_game(game_id) is None: raise HTTPException(404,f"Game '{game_id}' not found.")
    with db.get_conn() as conn:
        if concern: conn.execute("DELETE FROM features WHERE game_id=? AND concern=?",(game_id,concern.value))
        else: conn.execute("DELETE FROM features WHERE game_id=?",(game_id,))
