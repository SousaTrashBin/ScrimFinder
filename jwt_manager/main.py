from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from jwt_manager.core.db import init_db
from jwt_manager.core.security import init_keys
from jwt_manager.routers import auth


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    init_keys()
    yield


app = FastAPI(
    title="ScrimFinder JWT Manager",
    description=(
        "Authentication microservice — account creation, login, RS256 token "
        "issuance/validation, and JWKS endpoint for Istio integration.\n\n"
        "**Student:** Rodrigo Neto (fc59850)"
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/api/v1/auth/docs",
    openapi_url="/api/v1/auth/openapi.json",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)


@app.get("/api/v1/auth/", tags=["System"])
def root():
    return {"service": "ScrimFinder JWT Manager", "version": "1.0.0", "status": "ok"}


@app.get("/api/v1/auth/health", tags=["System"])
@app.get("/api/v1/auth/q/health/live", tags=["System"])
@app.get("/api/v1/auth/q/health/ready", tags=["System"])
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