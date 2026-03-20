"""analysis/api/main.py — Single FastAPI app. Run: uvicorn api.main:app --port 8000 --reload"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from analysis_api.core.config import cfg
from analysis_api.core.db import init_db, count_games, list_models
from analysis_api.routers import games, features, datasets, training, models, analysis

cfg.ensure_dirs()
init_db()

app = FastAPI(
    title="ScrimFinder Analysis API",
    description="""Unified ML platform for LoL match analysis.

""",
    version="2.0.0",
)

app.add_middleware(CORSMiddleware,allow_origins=["*"],allow_methods=["*"],allow_headers=["*"])

app.include_router(games.router)
app.include_router(features.router)
app.include_router(datasets.router)
app.include_router(training.router)
app.include_router(models.router)
app.include_router(analysis.router)

@app.get("/",tags=["System"],summary="API status")
def root():
    return {"service":"ScrimFinder Analysis API","version":"2.0.0","status":"ok",
            "active_models":[m["concern"] for m in list_models(active_only=True)],
            "games_ingested":count_games()}

@app.get("/health",tags=["System"],summary="Health check")
def health(): return {"status":"ok"}

@app.exception_handler(404)
async def not_found(req,exc): return JSONResponse(status_code=404, content={"code":404,"message":str(exc.detail)})

@app.exception_handler(500)
async def server_error(req,exc): return JSONResponse(status_code=500, content={"code":500,"message":"Internal server error."})
