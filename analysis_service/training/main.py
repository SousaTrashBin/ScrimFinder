"""
training/main.py
Training Service — FastAPI app running on port 8000.

Endpoints
---------
POST /train/trigger?concern=draft|build|performance|all
    Kick off an async training job. Returns a job ID.

GET  /train/jobs/{job_id}
    Poll the status and result of a training job.

GET  /train/jobs
    List all training jobs (current process lifetime).

GET  /registry/models
    List all model records in the registry.

GET  /registry/models/{model_id}
    Get a specific model record.

POST /registry/models/{model_id}/activate
    Manually promote any registered model to active status.
"""

from fastapi import FastAPI, Query, Path, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import Optional, Literal

from model_registry.db import init_db, list_models, get_model_by_id, activate_model
from training.runner import job_registry, run_job_async, JobStatus
import training.train_draft as train_draft
import training.train_build as train_build
import training.train_performance as train_performance

# Initialise the registry DB on startup
init_db()

app = FastAPI(
    title="LoL Training Service",
    description=(
        "Manages training jobs for the Draft, Build, and Performance models.\n\n"
        "Trained artifacts are stored on disk and registered in the Model Registry DB, "
        "where the analysis services pick them up automatically."
    ),
    version="1.0.0",
)
from fastapi.middleware.cors import CORSMiddleware
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

ConcernType = Literal["draft", "build", "performance", "all"]

_TRAINERS = {
    "draft": train_draft.train,
    "build": train_build.train,
    "performance": train_performance.train,
}


# ── Training endpoints ────────────────────────────────────────

@app.post(
    "/train/trigger",
    summary="Trigger a training job",
    description=(
        "Starts one or more training jobs asynchronously. "
        "Pass `concern=all` to retrain every model at once. "
        "Returns job IDs that can be polled via GET /train/jobs/{job_id}."
    ),
    tags=["Training"],
)
def trigger_training(
    concern: ConcernType = Query(..., description="Which model to train, or 'all'.")
):
    concerns = list(_TRAINERS.keys()) if concern == "all" else [concern]
    jobs = []
    for c in concerns:
        job = job_registry.create(concern=c)
        run_job_async(job, _TRAINERS[c])
        jobs.append(job.to_dict())
    return {"jobs": jobs}


@app.get(
    "/train/jobs/{job_id}",
    summary="Get training job status",
    tags=["Training"],
)
def get_job(job_id: str = Path(..., description="Job ID returned by /train/trigger.")):
    job = job_registry.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
    return job.to_dict()


@app.get(
    "/train/jobs",
    summary="List all training jobs",
    tags=["Training"],
)
def list_jobs(
    concern: Optional[str] = Query(None, description="Filter by concern."),
    status: Optional[JobStatus] = Query(None, description="Filter by status."),
):
    jobs = job_registry.all()
    if concern:
        jobs = [j for j in jobs if j.concern == concern]
    if status:
        jobs = [j for j in jobs if j.status == status]
    return {"jobs": [j.to_dict() for j in jobs]}


# ── Registry endpoints ────────────────────────────────────────

@app.get(
    "/registry/models",
    summary="List all registered models",
    tags=["Model Registry"],
)
def list_registry_models(
    concern: Optional[str] = Query(None, description="Filter by concern.")
):
    return {"models": list_models(concern=concern)}


@app.get(
    "/registry/models/{model_id}",
    summary="Get a specific model record",
    tags=["Model Registry"],
)
def get_registry_model(model_id: int = Path(..., description="Model record ID.")):
    record = get_model_by_id(model_id)
    if record is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    return record


@app.post(
    "/registry/models/{model_id}/activate",
    summary="Manually activate a registered model",
    description=(
        "Promotes the specified model to active status for its concern. "
        "All analysis services will hot-reload it within their poll interval."
    ),
    tags=["Model Registry"],
)
def manual_activate(model_id: int = Path(..., description="Model record ID to activate.")):
    record = get_model_by_id(model_id)
    if record is None:
        raise HTTPException(status_code=404, detail=f"Model id={model_id} not found.")
    activate_model(model_id)
    return {"message": f"Model {model_id} activated for concern='{record['concern']}'."}