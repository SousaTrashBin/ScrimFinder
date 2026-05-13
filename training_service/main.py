from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from training_service.routers import datasets, features, games, models, training


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Import here so env vars set before import are picked up correctly in tests
    from training_service.core.config import cfg
    from training_service.core.db import init_db

    cfg.ensure_dirs()
    init_db()

    from training_service.grpc_server import start_background_server, stop_server
    from training_service.rabbitmq_consumer import (
        start_background_consumer,
        stop_consumer,
    )

    start_background_server()
    start_background_consumer()
    yield
    stop_consumer()
    stop_server(grace=1)


app = FastAPI(
    title="ScrimFinder Training Service",
    description=(
        "Game ingestion, feature extraction, ML training and model registry.\n\n"
        "**Student:** Rodrigo Neto (fc59850)"
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/api/v1/training/docs",
    openapi_url="/api/v1/training/openapi.json",
)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)
app.include_router(games.router, prefix="/api/v1/training")
app.include_router(features.router, prefix="/api/v1/training")
app.include_router(datasets.router, prefix="/api/v1/training")
app.include_router(training.router, prefix="/api/v1/training")
app.include_router(models.router, prefix="/api/v1/training")


@app.get("/api/v1/training/", tags=["System"])
def root():
    from training_service.core.db import count_games, list_models

    return {
        "service": "ScrimFinder Training Service",
        "version": "1.0.0",
        "status": "ok",
        "active_models": [m["concern"] for m in list_models(active_only=True)],
        "games_ingested": count_games(),
    }


@app.get("/api/v1/training/q/health/live", tags=["System"])
@app.get("/api/v1/training/q/health/ready", tags=["System"])
@app.get("/api/v1/training/health", tags=["System"])
def health():
    return {"status": "ok"}


@app.exception_handler(404)
async def not_found(req, exc):
    return JSONResponse(
        status_code=404, content={"code": 404, "message": str(exc.detail)}
    )


@app.exception_handler(500)
async def server_error(req, exc):
    import traceback
    return JSONResponse(
        status_code=500,
        content={
            "code": 500,
            "message": str(exc),
            "detail": traceback.format_exc()
        }
    )
