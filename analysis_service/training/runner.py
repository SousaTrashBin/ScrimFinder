"""
training/runner.py
Manages async training jobs with progress reporting and cancellation.

Cancellation is cooperative — the training function checks job.cancelled
at safe checkpoints (between stages) and raises CancelledError if set.
The model artifact saved up to that point is retained in the registry.
"""

import uuid
import threading
from datetime import datetime, timezone
from typing import Callable, Optional
from enum import Enum


class JobStatus(str, Enum):
    PENDING   = "PENDING"
    RUNNING   = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED    = "FAILED"
    CANCELLED = "CANCELLED"


class CancelledError(Exception):
    pass


class TrainingJob:
    def __init__(self, job_id: str, concern: str):
        self.job_id        = job_id
        self.concern       = concern
        self.status        = JobStatus.PENDING
        self.created_at    = datetime.now(timezone.utc).isoformat()
        self.started_at:   Optional[str] = None
        self.completed_at: Optional[str] = None
        self.model_version: Optional[str] = None
        self.metrics:      Optional[dict] = None
        self.error:        Optional[str] = None
        self.filters:      dict = {}

        self.progress: int = 0
        self.stage:    str = "Waiting to start"

        self._cancelled = False
        self._lock = threading.Lock()

    def update_progress(self, progress: int, stage: str) -> None:
        with self._lock:
            if self._cancelled:
                raise CancelledError(f"Job {self.job_id} cancelled at {progress}% ({stage})")
            self.progress = min(progress, 100)
            self.stage    = stage
        print(f"[{self.concern}] {progress:>3}%  {stage}")

    def cancel(self) -> bool:
        with self._lock:
            if self.status in (JobStatus.PENDING, JobStatus.RUNNING):
                self._cancelled = True
                return True
            return False

    @property
    def cancelled(self) -> bool:
        return self._cancelled

    def to_dict(self) -> dict:
        with self._lock:
            return {
                "jobId":        self.job_id,
                "concern":      self.concern,
                "status":       self.status,
                "progress":     self.progress,
                "stage":        self.stage,
                "createdAt":    self.created_at,
                "startedAt":    self.started_at,
                "completedAt":  self.completed_at,
                "modelVersion": self.model_version,
                "metrics":      self.metrics,
                "error":        self.error,
                "filters":      self.filters,
            }


class JobRegistry:
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

    def latest_for_concern(self, concern: str) -> Optional[TrainingJob]:
        jobs = [j for j in self._jobs.values() if j.concern == concern]
        return max(jobs, key=lambda j: j.created_at) if jobs else None


job_registry = JobRegistry()


def run_job_async(job: TrainingJob, train_fn: Callable[[TrainingJob], None]) -> None:
    def _run():
        job.status     = JobStatus.RUNNING
        job.started_at = datetime.now(timezone.utc).isoformat()
        try:
            train_fn(job)
            if job.cancelled:
                job.status = JobStatus.CANCELLED
                job.stage  = f"Cancelled at {job.progress}% — partial model retained"
            else:
                job.status   = JobStatus.COMPLETED
                job.progress = 100
        except CancelledError as e:
            job.status = JobStatus.CANCELLED
            job.stage  = f"Cancelled at {job.progress}% — model retained if already saved"
            job.error  = str(e)
            print(f"[{job.concern}] CANCELLED at {job.progress}%")
        except Exception as exc:
            job.status = JobStatus.FAILED
            job.error  = str(exc)
            print(f"[{job.concern}] FAILED: {exc}")
        finally:
            job.completed_at = datetime.now(timezone.utc).isoformat()

    t = threading.Thread(
        target=_run, daemon=True,
        name=f"train-{job.concern}-{job.job_id[:8]}"
    )
    t.start()
