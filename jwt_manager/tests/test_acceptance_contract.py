import pytest
from fastapi.testclient import TestClient

from jwt_manager import main

pytestmark = pytest.mark.acceptance


def test_health_and_jwks_contract(monkeypatch):
    monkeypatch.setattr(main, "init_db", lambda: None)
    client = TestClient(main.app, raise_server_exceptions=False)

    assert client.get("/api/v1/auth/health").json() == {"status": "ok"}
    jwks = client.get("/api/v1/auth/jwks.json").json()
    assert jwks["keys"][0]["kty"] == "RSA"
    assert jwks["keys"][0]["alg"] == "RS256"


def test_openapi_exposes_auth_flow(monkeypatch):
    monkeypatch.setattr(main, "init_db", lambda: None)
    client = TestClient(main.app, raise_server_exceptions=False)
    paths = client.get("/api/v1/auth/openapi.json").json()["paths"]

    for path in (
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/validate",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/logout-all",
    ):
        assert path in paths
