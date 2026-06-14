from typing import Optional

from fastapi import APIRouter, HTTPException, Path, Query

from ..core import db
from ..core.schemas import (
    ErrorResponse,
    FeatureExtractRequest,
    FeatureExtractResponse,
    FeatureVector,
)

router = APIRouter(prefix="/features", tags=["Features"])


def _match_id(raw: dict) -> str:
    for key in ("matchId", "match_id", "gameId", "id"):
        if raw.get(key):
            return str(raw[key])
    metadata = raw.get("metadata")
    if isinstance(metadata, dict):
        for key in ("matchId", "match_id"):
            if metadata.get(key):
                return str(metadata[key])
    return "inline"


@router.post(
    "/extract",
    response_model=FeatureExtractResponse,
    summary="Extract features from a game",
    responses={404: {"model": ErrorResponse}, 422: {"model": ErrorResponse}},
)
def extract(body: FeatureExtractRequest):
    """
    Extract features for one or more concerns.

    - If `game_id` is provided, reads the game from DB.
    - If `raw_data` is provided, uses it directly.
    - If `store=True`, persists features to DB.
    """
    from ..core.feature_engineering import extract_features

    raw = None
    if body.game_id:
        game = db.get_game(body.game_id)
        if game is None:
            raise HTTPException(
                status_code=404, detail=f"Game '{body.game_id}' not found."
            )
        raw = game["raw_json"]
    elif body.raw_data:
        raw = body.raw_data
    else:
        raise HTTPException(
            status_code=422, detail="Provide either game_id or raw_data."
        )

    results = []
    response_game_id = body.game_id or _match_id(raw)
    for concern in body.concerns:
        vector, names = extract_features(raw, concern.value)
        if body.store:
            db.upsert_features(response_game_id, concern.value, vector, names)
        results.append(
            FeatureVector(
                game_id=response_game_id,
                concern=concern.value,
                feature_vector=vector,
                feature_names=names,
                schema_version="1",
                extracted_at="now",
            )
        )

    return FeatureExtractResponse(
        game_id=response_game_id,
        features=results,
        stored=body.store,
    )


@router.get(
    "",
    response_model=FeatureVector,
    summary="Get stored features for a game (by query param)",
    responses={404: {"model": ErrorResponse}},
)
def get_features_by_query(
    game_id: str = Query(..., description="Game ID to fetch features for"),
    concern: Optional[str] = Query(None, description="Optional concern filter"),
):
    """
    Get stored features for a specific game.

    - `game_id` is required.
    - `concern` is optional; if omitted, returns the first concern found.
    """
    rows = db.get_features(game_id, concern=concern)
    if not rows:
        raise HTTPException(
            status_code=404, detail=f"No features found for game '{game_id}'."
        )
    r = rows[0]
    return FeatureVector(
        game_id=r["game_id"],
        concern=r["concern"],
        feature_vector=r.get("feature_vector") or [],
        feature_names=r.get("feature_names") or [],
        schema_version=r.get("schema_version", "1"),
        extracted_at=str(r["extracted_at"]) if r.get("extracted_at") else None,
    )


@router.get(
    "/{game_id}",
    response_model=FeatureVector,
    summary="Get stored features for a game (by path)",
    responses={404: {"model": ErrorResponse}},
)
def get_features_by_path(game_id: str = Path(...)):
    """Legacy path-based endpoint — kept for backward compatibility."""
    rows = db.get_features(game_id)
    if not rows:
        raise HTTPException(
            status_code=404, detail=f"No features found for game '{game_id}'."
        )
    r = rows[0]
    return FeatureVector(
        game_id=r["game_id"],
        concern=r["concern"],
        feature_vector=r.get("feature_vector") or [],
        feature_names=r.get("feature_names") or [],
        schema_version=r.get("schema_version", "1"),
        extracted_at=str(r["extracted_at"]) if r.get("extracted_at") else None,
    )
