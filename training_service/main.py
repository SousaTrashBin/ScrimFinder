from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi.responses import JSONResponse

from training_service.core.config import cfg
from training_service.core.db import count_games, init_db, list_models
from training_service.routers import features, games, models, training

cfg.ensure_dirs()
init_db()


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("LIFESPAN STARTED", flush=True)
    from training_service.grpc_server import start_background_server, stop_server
    from training_service.rabbitmq_consumer import (
        start_background_consumer,
        stop_consumer,
    )

    start_background_server()
    start_background_consumer()
    yield
    print("LIFESPAN ENDED - Stopping gRPC", flush=True)
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
    root_path="/api/v1/training",
    docs_url="/docs",
    openapi_url="/openapi.json",
)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)
app.include_router(games.router)
app.include_router(features.router)
app.include_router(training.router)
app.include_router(models.router)


@app.get("/api/v1/training/openapi.json", include_in_schema=False)
def prefixed_openapi():
    return app.openapi()


@app.get("/api/v1/training/docs", include_in_schema=False)
def prefixed_docs():
    return get_swagger_ui_html(
        openapi_url="/api/v1/training/openapi.json",
        title="ScrimFinder Training Service - Swagger UI",
    )


@app.get("/", tags=["System"])
def root():
    return {
        "service": "ScrimFinder Training Service",
        "version": "1.0.0",
        "status": "ok",
        "active_models": [m["concern"] for m in list_models(active_only=True)],
        "games_ingested": count_games(),
    }


@app.get("/q/health/live", tags=["System"])
@app.get("/q/health/ready", tags=["System"])
@app.get("/health", tags=["System"])
def health():
    return {"status": "ok"}


@app.exception_handler(404)
async def not_found(req, exc):
    return JSONResponse(
        status_code=404, content={"code": 404, "message": str(exc.detail)}
    )


@app.exception_handler(500)
async def server_error(req, exc):
    return JSONResponse(
        status_code=500, content={"code": 500, "message": "Internal server error."}
    )
