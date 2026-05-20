import pytest
from fastapi.testclient import TestClient

from analysis_service.main import app

pytestmark = pytest.mark.acceptance


def test_public_health_and_openapi_paths():
    client = TestClient(app, raise_server_exceptions=False)

    assert client.get("/api/v1/analysis/health").json() == {"status": "ok"}
    paths = client.get("/api/v1/analysis/openapi.json").json()["paths"]
    for path in (
        "/api/v1/analysis/draft",
        "/api/v1/analysis/build",
        "/api/v1/analysis/player",
        "/api/v1/analysis/game",
        "/api/v1/analysis/champion",
    ):
        assert path in paths
