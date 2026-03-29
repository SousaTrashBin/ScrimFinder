from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
from training_service.core.config import cfg
from training_service.core.db import init_db, count_games, list_models
from training_service.routers import games, features, datasets, training, models

cfg.ensure_dirs()
init_db()

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("LIFESPAN STARTED", flush=True)
    from training_service.grpc_server import start_background_server
    start_background_server()
    yield

app = FastAPI(
    root_path="/api/v1/training",
    title="ScrimFinder Training Service",
    description="Game ingestion, feature extraction, dataset management, ML training and model registry.\n\n**Student:** Rodrigo Neto (fc59850)",
    version="1.0.0",
    lifespan=lifespan,
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(games.router)
app.include_router(features.router)
app.include_router(datasets.router)
app.include_router(training.router)
app.include_router(models.router)

@app.get("/", tags=["System"])
def root():
    return {"service": "ScrimFinder Training Service", "version": "1.0.0", "status": "ok",
            "active_models": [m["concern"] for m in list_models(active_only=True)],
            "games_ingested": count_games()}

@app.get("/health", tags=["System"])
def health(): return {"status": "ok"}

@app.exception_handler(404)
async def not_found(req, exc):
    return JSONResponse(status_code=404, content={"code": 404, "message": str(exc.detail)})

@app.exception_handler(500)
async def server_error(req, exc):
    return JSONResponse(status_code=500, content={"code": 500, "message": "Internal server error."})
