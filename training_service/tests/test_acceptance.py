import pytest
from fastapi.testclient import TestClient

from training_service.main import app

pytestmark = pytest.mark.acceptance


def test_public_health_and_openapi_paths():
    # We use a TestClient on the full app, but we don't need a live DB for health
    # because it just returns {"status": "ok"}
    client = TestClient(app, raise_server_exceptions=False)

    assert client.get("/api/v1/training/health").json() == {"status": "ok"}
    
    spec = client.get("/api/v1/training/openapi.json").json()
    paths = spec["paths"]
    
    # Check for presence of main functional paths
    for path in (
        "/api/v1/training/games",
        "/api/v1/training/features/extract",
        "/api/v1/training/jobs",
        "/api/v1/training/models",
    ):
        assert any(p.startswith(path) for p in paths)
