"""
training/runner.py
Manages async training jobs. Each job runs in a thread pool so the
FastAPI training service stays responsive during long training runs.
"""

import uuid
import threading
from datetime import datetime, timezone
from typing import Callable, Optional
from enum import Enum


class JobStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class TrainingJob:
    def __init__(self, job_id: str, concern: str):
        self.job_id = job_id
        self.concern = concern
        self.status = JobStatus.PENDING
        self.created_at = datetime.now(timezone.utc).isoformat()
        self.started_at: Optional[str] = None
        self.completed_at: Optional[str] = None
        self.model_version: Optional[str] = None
        self.metrics: Optional[dict] = None
        self.error: Optional[str] = None

    def to_dict(self) -> dict:
        return {
            "jobId": self.job_id,
            "concern": self.concern,
            "status": self.status,
            "createdAt": self.created_at,
            "startedAt": self.started_at,
            "completedAt": self.completed_at,
            "modelVersion": self.model_version,
            "metrics": self.metrics,
            "error": self.error,
        }


class JobRegistry:
    """In-memory store of all training jobs (process lifetime)."""

    def __init__(self):
        self._jobs: dict[str, TrainingJob] = {}
        self._lock = threading.Lock()

    def create(self, concern: str) -> TrainingJob:
        job = TrainingJob(job_id=str(uuid.uuid4()), concern=concern)
        with self._lock:
            self._jobs[job.job_id] = job
        return job

    def get(self, job_id: str) -> Optional[TrainingJob]:
        return self._jobs.get(job_id)

    def all(self) -> list[TrainingJob]:
        return list(self._jobs.values())


# Singleton used by training/main.py
job_registry = JobRegistry()


def run_job_async(job: TrainingJob, train_fn: Callable[[TrainingJob], None]) -> None:
    """Kick off a training function in a background thread."""
    def _run():
        job.status = JobStatus.RUNNING
        job.started_at = datetime.now(timezone.utc).isoformat()
        try:
            train_fn(job)
            job.status = JobStatus.COMPLETED
        except Exception as exc:
            job.status = JobStatus.FAILED
            job.error = str(exc)
        finally:
            job.completed_at = datetime.now(timezone.utc).isoformat()

    t = threading.Thread(target=_run, daemon=True, name=f"train-{job.concern}-{job.job_id[:8]}")
    t.start()