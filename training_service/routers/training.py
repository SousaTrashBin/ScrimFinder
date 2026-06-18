import threading
import uuid
from typing import Optional

from fastapi import APIRouter, HTTPException, Path, Query

from ..core import db
from ..core.db import now_iso
from ..core.schemas import (
    ErrorResponse,
    JobStatus,
    TrainingJobCreate,
    TrainingJobListResponse,
    TrainingJobResponse,
)

router = APIRouter(prefix="/jobs", tags=["Training"])
_cancel_flags: dict = {}


def _resp(r):
    return TrainingJobResponse(
        id=r["id"],
        concern=r["concern"],
        algorithm=r["algorithm"],
        status=r["status"],
        progress=r["progress"],
        stage=r["stage"],
        filters=r.get("filters") or {},
        metrics=r.get("metrics"),
        model_id=r.get("model_id"),
        error=r.get("error"),
        created_at=str(r["created_at"]) if r.get("created_at") else None,
        started_at=str(r["started_at"]) if r.get("started_at") else None,
        completed_at=str(r["completed_at"]) if r.get("completed_at") else None,
    )


def _run(job_id, concern, algorithm, filters, cancel):
    import importlib

    def report(pct, stage):
        if cancel.is_set():
            raise InterruptedError("Cancelled")
        db.update_job(job_id, progress=pct, stage=stage)

    if cancel.is_set():
        db.update_job(
            job_id, status="CANCELLED", stage="Cancelled", completed_at=now_iso()
        )
        return

    db.update_job(job_id, status="RUNNING", started_at=now_iso())
    try:

        def _get_module(concern: str, algorithm: str) -> str:
            defaults = {
                "draft": "gbm",
                "build": "random_forest",
                "performance": "gbm",
            }
            if algorithm in ("auto", ""):
                algorithm = defaults.get(concern, "gbm")

            # Store resolved algorithm back on job
            db.update_job(job_id, algorithm=algorithm)

            return {
                "draft": "training_service.training.train_draft",
                "build": "training_service.training.train_build",
                "performance": "training_service.training.train_performance",
            }.get(concern)

        module_path = _get_module(concern, algorithm)
        if module_path is None:
            raise ValueError(f"Unknown concern '{concern}'")
        mod = importlib.import_module(module_path)

        class _J:
            def __init__(self):
                self.job_id = job_id
                self.concern = concern
                self.filters = filters
                self.algorithm = algorithm
                self.metrics = None
                self.model_id = None

            def update_progress(self, p, s):
                report(p, s)

            @property
            def cancelled(self):
                return cancel.is_set()

        j = _J()
        mod.train(j)
        if cancel.is_set():
            raise InterruptedError("Cancelled")
        db.update_job(
            job_id,
            status="COMPLETED",
            progress=100,
            stage="Done",
            metrics=j.metrics or {},
            model_id=j.model_id,
            completed_at=now_iso(),
        )
    except InterruptedError:
        db.update_job(
            job_id, status="CANCELLED", stage="Cancelled", completed_at=now_iso()
        )
    except Exception as exc:
        db.update_job(
            job_id,
            status="FAILED",
            stage="Failed",
            error=str(exc),
            completed_at=now_iso(),
        )


@router.post(
    "",
    response_model=TrainingJobResponse,
    status_code=202,
    summary="Start a training job",
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def create_job(body: TrainingJobCreate):
    filters = {
        k: v
        for k, v in {
            "sample": body.sample,
            "limit": body.limit,
            "match_type": body.match_type,
        }.items()
        if v is not None
    }
    job_id = "job_" + uuid.uuid4().hex[:12]
    db.create_job(job_id, body.concern.value, body.algorithm.value, None, filters)
    cancel = threading.Event()
    _cancel_flags[job_id] = cancel
    threading.Thread(
        target=_run,
        args=(
            job_id,
            body.concern.value,
            body.algorithm.value,
            filters,
            cancel,
        ),
        daemon=True,
    ).start()
    return _resp(db.get_job(job_id))


@router.get(
    "",
    response_model=TrainingJobListResponse,
    summary="List jobs or get a specific job by ID",
)
def list_or_get_job(
    job_id: Optional[str] = Query(
        None, description="Specific job ID to fetch. If omitted, returns all jobs."
    ),
    concern: Optional[str] = Query(None, description="Filter by concern"),
    status: Optional[JobStatus] = Query(None, description="Filter by status"),
    limit: int = Query(100, ge=1, le=500),
):
    """
    List training jobs with optional filtering, or fetch a single job by ID.

    - If `job_id` is provided, returns that specific job (404 if not found).
    - Otherwise returns a filtered, paginated list.
    """
    if job_id:
        row = db.get_job(job_id)
        if row is None:
            raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
        return TrainingJobListResponse(jobs=[_resp(row)])

    rows = db.list_jobs(
        concern=concern, status=status.value if status else None, limit=limit
    )
    return TrainingJobListResponse(jobs=[_resp(r) for r in rows])


@router.delete(
    "/{job_id}",
    status_code=204,
    summary="Delete a specific job",
    responses={404: {"model": ErrorResponse}},
)
def delete_job(job_id: str = Path(...)):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    if row["status"] in ("PENDING", "RUNNING"):
        ev = _cancel_flags.get(job_id)
        if ev:
            ev.set()
    db.delete_job(job_id)
    return None


@router.delete(
    "",
    status_code=204,
    summary="Delete all jobs (bulk cleanup)",
    responses={409: {"model": ErrorResponse}},
)
def delete_all_jobs(
    status: Optional[JobStatus] = Query(
        None, description="Only delete jobs with this status"
    ),
    confirm: bool = Query(False, description="Must be true to actually delete"),
):
    if not confirm:
        raise HTTPException(
            status_code=409,
            detail="Add ?confirm=true to actually delete all jobs.",
        )
    jobs = db.list_jobs(status=status.value if status else None, limit=10000)
    for row in jobs:
        job_id = row["id"]
        if row["status"] in ("PENDING", "RUNNING"):
            ev = _cancel_flags.get(job_id)
            if ev:
                ev.set()
        db.delete_job(job_id)
    return None


@router.post(
    "/{job_id}/cancel",
    response_model=TrainingJobResponse,
    summary="Cancel a job",
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def cancel_job(
    job_id: str = Path(
        ..., description="Training job id", examples=["job_123456abcdef"]
    ),
):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    if row["status"] not in ("PENDING", "RUNNING"):
        raise HTTPException(
            status_code=409, detail=f"Job is '{row['status']}' — cannot cancel."
        )
    ev = _cancel_flags.get(job_id)
    if ev:
        ev.set()
    db.update_job(
        job_id,
        status="CANCELLED",
        stage="Cancelled via API",
        completed_at=now_iso(),
    )
    return _resp(db.get_job(job_id))


@router.post(
    "/{job_id}/deploy",
    response_model=TrainingJobResponse,
    summary="Deploy model from job",
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
def deploy_job(
    job_id: str = Path(
        ..., description="Training job id", examples=["job_123456abcdef"]
    ),
):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    if row["status"] != "COMPLETED":
        raise HTTPException(
            status_code=409,
            detail=f"Job is '{row['status']}' — only COMPLETED jobs can be deployed.",
        )

    if not row.get("model_id"):
        raise HTTPException(status_code=409, detail="No model_id recorded on this job.")
    try:
        db.activate_model(row["model_id"])
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return _resp(db.get_job(job_id))
