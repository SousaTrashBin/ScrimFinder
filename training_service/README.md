# Training Service

Python 3.11 / FastAPI service for data ingestion, feature extraction, model metadata, and training workflows. It also runs the gRPC server consumed by `analysis_service` and `match_history_service`.

## Local Development

```bash
python -m pip install -r requirements.txt
../scripts/python/generate-grpc.sh training_service
uvicorn training_service.main:app --reload --port 8000
```

Important environment variables:

- `LEAGUE_DB`
- `PLATFORM_DB`
- `MODELS_DIR`
- `GAMES_DIR`
- `DATASETS_DIR`
- `GRPC_PORT`
- `DETAIL_FILLING_URL`

## Tests

```bash
pytest
```

Generated gRPC files are intentionally not committed. Regenerate them with:

```bash
../scripts/python/generate-grpc.sh training_service
```

Dataset preparation helpers live in `datasets/`.
