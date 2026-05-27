"""
training_service/routers/models.py
All model metadata endpoints.
"""

from typing import Optional

from fastapi import APIRouter, HTTPException, Path, Query

from training_service.core import db
from training_service.core.schemas import ErrorResponse, ModelListResponse, ModelMeta

router = APIRouter(prefix="/models", tags=["Models"])


def _meta(r):
    # BigQuery returns datetime objects — convert to ISO strings for Pydantic
    created = r.get("created_at")
    activated = r.get("activated_at")
    if hasattr(created, "isoformat"):
        created = created.isoformat()
    if hasattr(activated, "isoformat"):
        activated = activated.isoformat()

    return ModelMeta(
        id=r["id"],
        concern=r["concern"],
        algorithm=r["algorithm"],
        version=r["version"],
        metrics=r.get("metrics") or {},
        hyperparams=r.get("hyperparams") or {},
        is_active=bool(r["is_active"]),
        created_at=created,
        activated_at=activated,
    )


@router.get(
    "", response_model=ModelListResponse, summary="List models or get one by ID"
)
def list_or_get_models(
    model_id: Optional[str] = Query(None, description="Specific model ID to fetch"),
    concern: Optional[str] = Query(None, description="Filter by concern"),
    active_only: bool = Query(False, description="Only active models"),
):
    """
    List all models with optional filtering, or fetch a single model by ID.

    - If `model_id` is provided, returns that specific model (404 if not found).
    - Otherwise returns a filtered list based on `concern` and `active_only`.
    """
    if model_id:
        row = db.get_model_by_id(model_id)
        if row is None:
            raise HTTPException(
                status_code=404, detail=f"Model id={model_id} not found."
            )
        return ModelListResponse(models=[_meta(row)])

    return ModelListResponse(
        models=[
            _meta(r) for r in db.list_models(concern=concern, active_only=active_only)
        ]
    )


@router.get("/active", response_model=ModelListResponse, summary="List active models")
def list_active():
    return ModelListResponse(
        models=[_meta(r) for r in db.list_models(active_only=True)]
    )


@router.get(
    "/{model_id}",
    response_model=ModelMeta,
    summary="Get model metadata by path",
    responses={404: {"model": ErrorResponse}},
)
def get_model(model_id: str = Path(...)):
    row = db.get_model_by_id(model_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    return _meta(row)


@router.post(
    "/{model_id}/activate",
    response_model=ModelMeta,
    summary="Activate a model",
    responses={404: {"model": ErrorResponse}},
)
def activate(model_id: str = Path(...)):
    if db.get_model_by_id(model_id) is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    try:
        db.activate_model(model_id)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return _meta(db.get_model_by_id(model_id))


@router.post(
    "/{model_id}/deactivate",
    response_model=ModelMeta,
    summary="Deactivate a model",
    responses={404: {"model": ErrorResponse}},
)
def deactivate(model_id: str = Path(...)):
    if db.get_model_by_id(model_id) is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    db.deactivate_model(model_id)
    return _meta(db.get_model_by_id(model_id))


@router.delete(
    "/{model_id}",
    status_code=204,
    summary="Delete a model",
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def delete_model(model_id: str = Path(...)):
    row = db.get_model_by_id(model_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    if row["is_active"]:
        raise HTTPException(
            status_code=409,
            detail="Cannot delete an active model. Deactivate it first.",
        )
    db.delete_model(model_id)
