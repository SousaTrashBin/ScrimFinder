# ScrimFinder API Refactoring Specification

## Context
The ScrimFinder project is migrating from SQLite/PostgreSQL to BigQuery for ML platform metadata. The training_service and analysis_service need API improvements for better UX, cleaner endpoints, and proper BigQuery integration.

---

## 1. Training Service API Changes

### 1.1 Current Problems
- TrainingJobCreate.match_type defaults to "string" in Swagger UI (misleading)
- No way to delete jobs (pollutes the jobs list quickly)
- Two separate endpoints for listing (GET /jobs) and getting one job (GET /jobs/{job_id})
- artifact BYTES column in BigQuery causes json.dumps() serialization crash when registering models

### 1.2 Schema Changes (training_service/core/schemas.py)

Update TrainingJobCreate:
```python
class TrainingJobCreate(BaseModel):
    concern: Concern
    algorithm: Algorithm = Algorithm.AUTO
    sample: Optional[float] = Field(None, ge=0.01, le=1.0, description="Fraction of dataset to sample (0.01-1.0)")
    limit: Optional[int] = Field(None, ge=1, le=100000, description="Max rows to train on")
    match_type: Optional[str] = Field(None, description="Filter by match type (e.g., CLASSIC, ARAM). Leave empty for all.")
```

Key change: match_type defaults to None (not "string").

Add missing response models if needed:
```python
class TrainingJobResponse(BaseModel):
    id: str
    concern: str
    algorithm: str
    status: str
    progress: int
    stage: str
    filters: dict
    metrics: Optional[dict] = None
    model_id: Optional[str] = None
    error: Optional[str] = None
    created_at: Optional[str] = None
    started_at: Optional[str] = None
    completed_at: Optional[str] = None

class TrainingJobListResponse(BaseModel):
    jobs: list[TrainingJobResponse]

class ErrorResponse(BaseModel):
    code: int
    message: str
    details: Optional[str] = None
```

### 1.3 Router Changes (training_service/routers/training.py)

#### Merge list + get into single endpoint
Replace the two separate endpoints with one unified GET /jobs:

```python
@router.get(
    "",
    response_model=TrainingJobListResponse,
    summary="List jobs or get a specific job by ID",
)
def list_or_get_job(
    job_id: Optional[str] = Query(None, description="Specific job ID to fetch. If omitted, returns all jobs."),
    concern: Optional[str] = Query(None),
    status: Optional[JobStatus] = Query(None),
    limit: int = Query(100, ge=1, le=500),
):
    if job_id:
        row = db.get_job(job_id)
        if row is None:
            raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found.")
        return TrainingJobListResponse(jobs=[_resp(row)])

    rows = db.list_jobs(
        concern=concern, status=status.value if status else None, limit=limit
    )
    return TrainingJobListResponse(jobs=[_resp(r) for r in rows])
```

Remove the old separate GET /jobs/{job_id} endpoint.

#### Add DELETE endpoints
```python
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
    status: Optional[JobStatus] = Query(None, description="Only delete jobs with this status"),
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
```

### 1.4 DB Layer Fix (training_service/core/db.py)

#### Fix register_model() — do not pass bytes as JSON
The artifact BYTES column should be passed as a BYTES parameter, not inside json.dumps().

Current broken code passes artifact (bytes) in the params list, and _bq_type returns STRING for bytes, causing serialization issues.

Fix: Update _bq_type to handle bytes:

```python
def _bq_type(value: Any) -> str:
    if isinstance(value, bool):
        return "BOOL"
    if isinstance(value, int):
        return "INT64"
    if isinstance(value, float):
        return "FLOAT64"
    if isinstance(value, bytes):
        return "BYTES"
    if isinstance(value, str):
        return "STRING"
    return "STRING"
```

The register_model SQL already uses @p6 directly for artifact (no PARSE_JSON), which is correct.

#### Add delete_job() if missing
```python
def delete_job(job_id: str) -> bool:
    sql = f"DELETE FROM `{cfg.BQ_PROJECT}.{cfg.BQ_PLATFORM_DATASET}.training_jobs` WHERE id = @p0"
    _bq_query(sql, [job_id])
    return True
```

### 1.5 Data Loader Fix (training_service/training/data_loader.py)

Already fixed in previous iteration — uses _bq_query() with %s placeholders instead of pd.read_sql() with ?. Ensure this is in place.

---

## 2. Analysis Service API Changes

### 2.1 Current Problems
- analysis_service/core/db.py missing location parameter (causes "not found in location US" for EU datasets)
- analysis_service/champion/queries.py already uses %s (correct), but old container images had ?

### 2.2 DB Layer Fix (analysis_service/core/db.py)

Update _bq_query() to pass location:

```python
def _bq_query(sql: str, params: Optional[List[Any]] = None) -> bigquery.table.RowIterator:
    client = get_bq_client()
    job_config = QueryJobConfig()
    if params:
        job_config.query_parameters = [
            ScalarQueryParameter(f"p{i}", _bq_type(p), p)
            for i, p in enumerate(params)
        ]
        parts = sql.split("%s")
        sql = "".join(f"{part}@p{i}" for i, part in enumerate(parts[:-1])) + parts[-1]
    location = getattr(cfg, "BQ_LOCATION", None)
    return client.query(sql, job_config=job_config, location=location).result()
```

Also add _bq_type() with BYTES support:

```python
def _bq_type(value: Any) -> str:
    if isinstance(value, bool):
        return "BOOL"
    if isinstance(value, int):
        return "INT64"
    if isinstance(value, float):
        return "FLOAT64"
    if isinstance(value, bytes):
        return "BYTES"
    if isinstance(value, str):
        return "STRING"
    return "STRING"
```

### 2.3 Endpoint Pattern

Analysis service endpoints (/draft, /build, /player, /game, /champion) are operation-specific and should remain separate since each has different request/response schemas.

However, if adding list/get endpoints for any resource in the future, use the same unified pattern:
```python
# GET /resource?resource_id=xxx  -> single item wrapped in list
# GET /resource                  -> list all
```

---

## 3. BigQuery Schema Notes

Ensure bq_schema.py in both services declares artifact as BYTES (already correct):
```sql
CREATE TABLE IF NOT EXISTS `{project}.{platform}.models` (
    id STRING NOT NULL,
    ...
    artifact BYTES,
    ...
)
```

---

## 4. Docker Rebuild Commands (Windows PowerShell)

After ALL file changes:

```powershell
# 1. Stop everything
docker-compose -f docker-compose.test.yml down

# 2. Nuke old images (optional but recommended)
docker rmi scrimfinder-training-service scrimfinder-analysis-service

# 3. Rebuild both services from scratch
docker-compose -f docker-compose.test.yml build --no-cache training-service analysis-service

# 4. Start
docker-compose -f docker-compose.test.yml up -d

# 5. Verify file contents inside containers
docker exec scrimfinder-training-service-1 cat /app/training_service/core/db.py | Select-String "PARSE_JSON"
docker exec scrimfinder-analysis-service-1 cat /app/analysis_service/champion/queries.py | Select-String "LOWER"
```

---

## 5. Test Commands

### Training Service
```powershell
# Create job (clean, no bogus defaults)
curl -X POST "http://localhost:8000/api/v1/training/jobs" `
  -H "Content-Type: application/json" `
  -d '{"concern": "draft", "sample": 0.01, "limit": 1000}'

# List all jobs
curl "http://localhost:8000/api/v1/training/jobs"

# Get specific job (unified endpoint)
curl "http://localhost:8000/api/v1/training/jobs?job_id=job_xxx"

# Delete one job
curl -X DELETE "http://localhost:8000/api/v1/training/jobs/job_xxx"

# Bulk delete all FAILED jobs
curl -X DELETE "http://localhost:8000/api/v1/training/jobs?status=FAILED&confirm=true"

# Bulk delete ALL jobs (USE WITH CAUTION)
curl -X DELETE "http://localhost:8000/api/v1/training/jobs?confirm=true"
```

### Analysis Service
```powershell
# Champion analysis (valid JSON, no trailing comma)
curl -X POST "http://localhost:8001/api/v1/analysis/champion" `
  -H "Content-Type: application/json" `
  -d '{"champion": "Jinx"}'

# With optional filters
curl -X POST "http://localhost:8001/api/v1/analysis/champion" `
  -H "Content-Type: application/json" `
  -d '{"champion": "Jinx", "position": "BOTTOM", "match_type": "CLASSIC"}'
```

---

## 6. Files to Modify Summary

| File | Service | Changes |
|------|---------|---------|
| training_service/core/schemas.py | Training | Fix TrainingJobCreate defaults |
| training_service/routers/training.py | Training | Merge list/get, add DELETE endpoints |
| training_service/core/db.py | Training | Fix _bq_type for BYTES, add delete_job() |
| training_service/training/data_loader.py | Training | Use _bq_query() with %s (already done) |
| analysis_service/core/db.py | Analysis | Add location parameter, add BYTES type |
| analysis_service/core/config.py | Analysis | Ensure BQ_LOCATION exists (already done) |

---

## 7. Critical Reminders

1. Never use ? placeholders — always use %s for BigQuery queries in this codebase (the _bq_query helper converts %s to @pN).
2. Never set job_config.location — pass location to client.query() instead.
3. Bytes go to BYTES columns — do not json.dumps() bytes; pass them as raw parameters with proper BYTES type.
4. Rebuild with --no-cache after any .py file change — Docker layer caching is aggressive.
5. Verify inside container with docker exec ... cat ... | Select-String before testing endpoints.
