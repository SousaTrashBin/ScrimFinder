"""
analysis_service/main.py â€” REST API on port 8001.
Serves real-time ML predictions and champion statistics.
Reads trained models from the shared volume written by the Training Service.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from analysis_service.core.config import cfg
from analysis_service.routers import analysis

cfg.ensure_dirs()

app = FastAPI(
    root_path="/api/v1/analysis",
    title="ScrimFinder Analysis Service",
    description="Real-time draft, build, player performance and champion analysis.\n\n**Student:** Rodrigo Neto (fc59850)",
    version="1.0.0",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(analysis.router)


@app.get("/", tags=["System"])
def root():
    return {"service": "ScrimFinder Analysis Service", "version": "1.0.0", "status": "ok"}


@app.get("/health", tags=["System"])
def health():
    return {"status": "ok"}


@app.exception_handler(404)
async def not_found(req, exc):
    return JSONResponse(status_code=404, content={"code": 404, "message": str(exc.detail)})


@app.exception_handler(500)
async def server_error(req, exc):
    return JSONResponse(status_code=500, content={"code": 500, "message": "Internal server error."})
