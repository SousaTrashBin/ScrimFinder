"""
analysis_service/main.py â€” REST API on port 8001.
Serves real-time ML predictions and champion statistics.
Reads trained models from the shared volume written by the Training Service.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi.responses import JSONResponse

from analysis_service.core.config import cfg
from analysis_service.routers import analysis

cfg.ensure_dirs()

app = FastAPI(
    title="ScrimFinder Analysis Service",
    description="""
    Real-time draft, build, player performance and champion analysis.\n
    \n**Student:** Rodrigo Neto (fc59850)
    """,
    version="1.0.0",
    root_path="/api/v1/analysis",
    docs_url="/docs",
    openapi_url="/openapi.json",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(analysis.router)


@app.get("/api/v1/analysis/openapi.json", include_in_schema=False)
def prefixed_openapi():
    return app.openapi()


@app.get("/api/v1/analysis/docs", include_in_schema=False)
def prefixed_docs():
    return get_swagger_ui_html(
        openapi_url="/api/v1/analysis/openapi.json",
        title="ScrimFinder Analysis Service - Swagger UI",
    )


@app.get("/", tags=["System"])
def root():
    return {"service": "ScrimFinder Analysis Service", "version": "1.0.0", "status": "ok"}


@app.get("/q/health/live", tags=["System"])
@app.get("/q/health/ready", tags=["System"])
@app.get("/health", tags=["System"])
def health():
    return {"status": "ok"}


@app.exception_handler(404)
async def not_found(req, exc):
    return JSONResponse(status_code=404, content={"code": 404, "message": str(exc.detail)})


@app.exception_handler(500)
async def server_error(req, exc):
    return JSONResponse(status_code=500, content={"code": 500, "message": "Internal server error."})
