import threading, uuid
from typing import Optional
from fastapi import APIRouter, HTTPException, Path, Query
from training_service.core import db
from training_service.core.db import now_iso
from training_service.core.schemas import (
    TrainingJobCreate, TrainingJobResponse, TrainingJobListResponse, JobStatus, ErrorResponse)

router = APIRouter(prefix="/training/jobs", tags=["Training"])
_cancel_flags: dict = {}

def _resp(r):
    return TrainingJobResponse(id=r["id"], concern=r["concern"], algorithm=r["algorithm"],
        dataset_id=r.get("dataset_id"), status=r["status"], progress=r["progress"],
        stage=r["stage"], filters=r.get("filters") or {}, metrics=r.get("metrics"),
        model_id=r.get("model_id"), error=r.get("error"), created_at=r["created_at"],
        started_at=r.get("started_at"), completed_at=r.get("completed_at"))

def _run(job_id, concern, algorithm, dataset_id, filters, cancel):
    import importlib
    def report(pct, stage):
        if cancel.is_set(): raise InterruptedError("Cancelled")
        db.update_job(job_id, progress=pct, stage=stage)
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
            def update_progress(self, p, s): report(p, s)
            @property
            def cancelled(self): return cancel.is_set()
        j = _J(); mod.train(j)
        db.update_job(job_id, status="COMPLETED", progress=100, stage="Done",
                      metrics=j.metrics or {}, model_id=j.model_id, completed_at=now_iso())
    except InterruptedError:
        db.update_job(job_id, status="CANCELLED", stage="Cancelled", completed_at=now_iso())
    except Exception as exc:
        db.update_job(job_id, status="FAILED", stage="Failed", error=str(exc), completed_at=now_iso())

@router.post("", response_model=TrainingJobResponse, status_code=202, summary="Start a training job",
             responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}})
def create_job(body: TrainingJobCreate):
    if body.dataset_id:
        ds = db.get_dataset(body.dataset_id)
        if ds is None:
            raise HTTPException(status_code=404, detail=f"Dataset '{body.dataset_id}' not found.")
        if ds["status"] != "ready":
            raise HTTPException(status_code=409, detail=f"Dataset status is '{ds['status']}', must be 'ready'.")
    filters = {k: v for k, v in {"sample": body.sample, "limit": body.limit,
                                   "match_type": body.match_type}.items() if v is not None}
    job_id = "job_" + uuid.uuid4().hex[:12]
    db.create_job(job_id, body.concern.value, body.algorithm.value, body.dataset_id, filters)
    cancel = threading.Event(); _cancel_flags[job_id] = cancel
    threading.Thread(target=_run, args=(job_id, body.concern.value, body.algorithm.value,
                     body.dataset_id, filters, cancel), daemon=True).start()
    return _resp(db.get_job(job_id))

@router.get("", response_model=TrainingJobListResponse, summary="List jobs")
def list_jobs(concern: Optional[str]=None, status: Optional[JobStatus]=None,
              limit: int=Query(100, ge=1, le=500)):
    rows = db.list_jobs(concern=concern, status=status.value if status else None, limit=limit)
    return TrainingJobListResponse(jobs=[_resp(r) for r in rows])

@router.get("/{job_id}", response_model=TrainingJobResponse, summary="Get job status",
            responses={404: {"model": ErrorResponse}})
def get_job(job_id: str = Path(...)):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    return _resp(row)

@router.post("/{job_id}/cancel", response_model=TrainingJobResponse, summary="Cancel a job",
             responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}})
def cancel_job(job_id: str = Path(...)):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    if row["status"] not in ("PENDING", "RUNNING"):
        raise HTTPException(status_code=409, detail=f"Job is '{row['status']}' â€” cannot cancel.")
    ev = _cancel_flags.get(job_id)
    if ev:
        ev.set()
    else:
        db.update_job(job_id, status="CANCELLED", stage="Cancelled via API", completed_at=now_iso())
    return _resp(db.get_job(job_id))

@router.post("/{job_id}/deploy", response_model=TrainingJobResponse, summary="Deploy model from job",
             responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}})
def deploy_job(job_id: str = Path(...)):
    row = db.get_job(job_id)
    if row is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    if row["status"] != "COMPLETED":
        raise HTTPException(status_code=409, detail=f"Job is '{row['status']}' â€” only COMPLETED jobs can be deployed.")
    if not row.get("model_id"):
        raise HTTPException(status_code=409, detail="No model_id recorded on this job.")
    try:
        db.activate_model(row["model_id"])
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return _resp(db.get_job(job_id))
