# Analysis Service

Python 3.11 / FastAPI service for champion, draft, build, and performance analysis. It reads the League dataset directly and uses gRPC to query active training model metadata from `training_service`.

## Local Development

```bash
python -m pip install -r requirements.txt
../scripts/python/generate-grpc.sh analysis_service
uvicorn analysis_service.main:app --reload --port 8001
```

Important environment variables:

- `LEAGUE_DB`
- `PLATFORM_DB`
- `MODELS_DIR`
- `TRAINING_GRPC_URL`

## Tests

```bash
pytest
```

Generated gRPC files are intentionally not committed. Regenerate them with:

```bash
../scripts/python/generate-grpc.sh analysis_service
```
