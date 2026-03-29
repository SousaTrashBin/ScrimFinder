import uuid
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, HTTPException, Path

from training_service.core import db
from training_service.core.schemas import (
    DatasetBuildRequest,
    DatasetCreateRequest,
    DatasetListResponse,
    DatasetMeta,
    ErrorResponse,
)

router = APIRouter(prefix="/datasets", tags=["Datasets"])


def _meta(r):
    return DatasetMeta(
        id=r["id"],
        name=r["name"],
        description=r.get("description", ""),
        concern=r["concern"],
        filters=r["filters"],
        game_count=r["game_count"],
        row_count=r["row_count"],
        status=r["status"],
        created_at=r["created_at"],
        built_at=r.get("built_at"),
        file_path=r.get("file_path"),
    )


def _build_task(ds_id, filters, concern):
    try:
        db.update_dataset_status(ds_id, status="building")
        # TODO: from training_service.datasets.builder import build
        db.update_dataset_status(
            ds_id, status="ready", game_count=db.count_games(), row_count=0
        )
    except Exception:
        db.update_dataset_status(ds_id, status="error")


@router.post(
    "", response_model=DatasetMeta, status_code=201, summary="Register dataset"
)
def create_dataset(body: DatasetCreateRequest):
    ds_id = "ds_" + uuid.uuid4().hex[:12]
    db.insert_dataset(
        ds_id,
        body.name,
        body.concern.value,
        body.filters.model_dump(exclude_none=True),
        body.description,
    )
    return _meta(db.get_dataset(ds_id))


@router.post(
    "/build",
    response_model=DatasetMeta,
    status_code=202,
    summary="Create and build dataset",
)
def build_dataset(body: DatasetBuildRequest, background_tasks: BackgroundTasks):
    ds_id = "ds_" + uuid.uuid4().hex[:12]
    filters = body.filters.model_dump(exclude_none=True)
    db.insert_dataset(ds_id, body.name, body.concern.value, filters, body.description)
    background_tasks.add_task(_build_task, ds_id, filters, body.concern.value)
    return _meta(db.get_dataset(ds_id))


@router.get("", response_model=DatasetListResponse, summary="List datasets")
def list_datasets(concern: Optional[str] = None):
    return DatasetListResponse(
        datasets=[_meta(r) for r in db.list_datasets(concern=concern)]
    )


@router.get(
    "/{dataset_id}",
    response_model=DatasetMeta,
    summary="Get dataset",
    responses={404: {"model": ErrorResponse}},
)
def get_dataset(dataset_id: str = Path(...)):
    row = db.get_dataset(dataset_id)
    if row is None:
        raise HTTPException(
            status_code=404, detail=f"Dataset '{dataset_id}' not found."
        )
    return _meta(row)


@router.post(
    "/{dataset_id}/build",
    response_model=DatasetMeta,
    status_code=202,
    summary="Rebuild dataset",
    responses={404: {"model": ErrorResponse}},
)
def rebuild_dataset(dataset_id: str, background_tasks: BackgroundTasks):
    row = db.get_dataset(dataset_id)
    if row is None:
        raise HTTPException(
            status_code=404, detail=f"Dataset '{dataset_id}' not found."
        )
    background_tasks.add_task(_build_task, dataset_id, row["filters"], row["concern"])
    return _meta(row)


@router.delete(
    "/{dataset_id}",
    status_code=204,
    summary="Delete dataset",
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def delete_dataset(dataset_id: str = Path(...)):
    if db.get_dataset(dataset_id) is None:
        raise HTTPException(
            status_code=404, detail=f"Dataset '{dataset_id}' not found."
        )
    with db.get_conn() as conn:
        active = conn.execute(
            "SELECT COUNT(*) FROM training_jobs WHERE dataset_id=? AND status IN ('PENDING','RUNNING')",
            (dataset_id,),
        ).fetchone()[0]
    if active:
        raise HTTPException(
            status_code=409,
            detail=f"Dataset has {active} active job(s). Cancel them first.",
        )
    db.delete_dataset(dataset_id)
