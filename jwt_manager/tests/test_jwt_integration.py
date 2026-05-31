"""
jwt_manager/tests/test_jwt_integration.py

Smoke tests for the JWT Manager service.
Uses TestClient (no live DB/Redis needed — mocks both layers).
Run: pytest jwt_manager/tests/
"""

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

pytestmark = pytest.mark.integration

# ── Fixtures & mocks ──────────────────────────────────────────────────────────

FAKE_USER = {
    "id": "00000000-0000-0000-0000-000000000001",
    "username": "testuser",
    "email": "test@scrimfinder.gg",
    "password_hash": "",  # filled in setup
    "is_active": True,
}


@pytest.fixture(autouse=True)
def mock_db_and_redis():
    """Patch all DB and Redis calls so tests run without infrastructure."""
    from ..core import security

    FAKE_USER["password_hash"] = security.hash_password("Password1!")

    with (
        patch("jwt_manager.core.db.init_db"),
        patch("jwt_manager.core.db.get_user_by_username") as mock_get_by_uname,
        patch("jwt_manager.core.db.get_user_by_email") as mock_get_by_email,
        patch("jwt_manager.core.db.get_user_by_id") as mock_get_by_id,
        patch("jwt_manager.core.db.create_user") as mock_create,
        patch("jwt_manager.core.db.store_refresh_token"),
        patch("jwt_manager.core.db.cache_access_token"),
        patch("jwt_manager.core.db.get_cached_session") as mock_session,
        patch("jwt_manager.core.db.invalidate_access_token"),
        patch("jwt_manager.core.db.invalidate_all_user_sessions"),
        patch("jwt_manager.core.db.revoke_all_user_tokens"),
        patch("jwt_manager.core.db.get_refresh_token") as mock_get_rt,
        patch("jwt_manager.core.db.revoke_refresh_token"),
    ):
        # Defaults
        mock_get_by_uname.return_value = None
        mock_get_by_email.return_value = None
        mock_get_by_id.return_value = FAKE_USER
        mock_create.return_value = FAKE_USER
        mock_session.return_value = str(FAKE_USER["id"])

        yield {
            "get_by_uname": mock_get_by_uname,
            "get_by_email": mock_get_by_email,
            "get_by_id": mock_get_by_id,
            "create": mock_create,
            "session": mock_session,
            "get_rt": mock_get_rt,
        }


@pytest.fixture
def client():
    from ..main import app

    return TestClient(app, raise_server_exceptions=True)


# ── Helpers ───────────────────────────────────────────────────────────────────


def _login(client, mocks):
    mocks["get_by_uname"].return_value = FAKE_USER
    resp = client.post(
        "/api/v1/auth/login", json={"username": "testuser", "password": "Password1!"}
    )
    assert resp.status_code == 200
    return resp.json()


# ── Health ────────────────────────────────────────────────────────────────────


def test_health(client):
    assert client.get("/api/v1/auth/health").status_code == 200


def test_health_live(client):
    assert client.get("/api/v1/auth/q/health/live").status_code == 200


def test_health_ready(client):
    assert client.get("/api/v1/auth/q/health/ready").status_code == 200


# ── Register ──────────────────────────────────────────────────────────────────


def test_register_success(client, mock_db_and_redis):
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "username": "newuser",
            "email": "new@scrimfinder.gg",
            "password": "SecurePass1!",
        },
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["username"] == "testuser"  # mock returns FAKE_USER
    assert "id" in body


def test_register_duplicate_username(client, mock_db_and_redis):
    mock_db_and_redis["get_by_uname"].return_value = FAKE_USER
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "username": "testuser",
            "email": "other@scrimfinder.gg",
            "password": "SecurePass1!",
        },
    )
    assert resp.status_code == 409


def test_register_duplicate_email(client, mock_db_and_redis):
    mock_db_and_redis["get_by_email"].return_value = FAKE_USER
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "username": "newuser2",
            "email": "test@scrimfinder.gg",
            "password": "SecurePass1!",
        },
    )
    assert resp.status_code == 409


def test_register_weak_password(client, mock_db_and_redis):
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "username": "newuser3",
            "email": "new3@scrimfinder.gg",
            "password": "short",
        },
    )
    assert resp.status_code == 422


def test_register_short_username(client, mock_db_and_redis):
    resp = client.post(
        "/api/v1/auth/register",
        json={
            "username": "ab",
            "email": "ab@scrimfinder.gg",
            "password": "SecurePass1!",
        },
    )
    assert resp.status_code == 422


# ── Login ─────────────────────────────────────────────────────────────────────


def test_login_success(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)
    assert "access_token" in tokens
    assert "refresh_token" in tokens
    assert tokens["token_type"] == "Bearer"


def test_login_wrong_password(client, mock_db_and_redis):
    mock_db_and_redis["get_by_uname"].return_value = FAKE_USER
    resp = client.post(
        "/api/v1/auth/login", json={"username": "testuser", "password": "WrongPass!"}
    )
    assert resp.status_code == 401


def test_login_unknown_user(client, mock_db_and_redis):
    resp = client.post(
        "/api/v1/auth/login", json={"username": "nobody", "password": "Password1!"}
    )
    assert resp.status_code == 401


def test_login_inactive_user(client, mock_db_and_redis):
    inactive = {**FAKE_USER, "is_active": False}
    mock_db_and_redis["get_by_uname"].return_value = inactive
    resp = client.post(
        "/api/v1/auth/login", json={"username": "testuser", "password": "Password1!"}
    )
    assert resp.status_code == 403


# ── Validate ──────────────────────────────────────────────────────────────────


def test_validate_success(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)
    resp = client.get(
        "/api/v1/auth/validate",
        headers={"Authorization": f"Bearer {tokens['access_token']}"},
    )
    assert resp.status_code == 200
    assert resp.json()["username"] == "testuser"


def test_validate_missing_header(client):
    """HTTPBearer returns 401 when Authorization header is absent."""
    resp = client.get("/api/v1/auth/validate")
    assert resp.status_code == 401


def test_validate_invalid_token(client):
    resp = client.get(
        "/api/v1/auth/validate", headers={"Authorization": "Bearer this.is.garbage"}
    )
    assert resp.status_code == 401


def test_validate_session_evicted(client, mock_db_and_redis):
    """Token is valid JWT but Redis session was invalidated (e.g. logout)."""
    tokens = _login(client, mock_db_and_redis)
    mock_db_and_redis["session"].return_value = None  # simulate evicted session
    resp = client.get(
        "/api/v1/auth/validate",
        headers={"Authorization": f"Bearer {tokens['access_token']}"},
    )
    assert resp.status_code == 401


# ── Refresh ───────────────────────────────────────────────────────────────────


def test_refresh_success(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)

    from ..core import security

    payload = security.decode_token(tokens["refresh_token"])

    mock_db_and_redis["get_rt"].return_value = {
        "jti": payload["jti"],
        "user_id": FAKE_USER["id"],
        "revoked": False,
    }
    mock_db_and_redis["get_by_id"].return_value = FAKE_USER

    resp = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    assert resp.status_code == 200
    new_tokens = resp.json()
    assert "access_token" in new_tokens
    assert "refresh_token" in new_tokens


def test_refresh_revoked_token(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)
    mock_db_and_redis["get_rt"].return_value = {"revoked": True}
    resp = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    assert resp.status_code == 401


def test_refresh_with_access_token(client, mock_db_and_redis):
    """Passing an access token to /refresh must be rejected."""
    tokens = _login(client, mock_db_and_redis)
    resp = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": tokens["access_token"]}
    )
    assert resp.status_code == 401


# ── Logout ────────────────────────────────────────────────────────────────────


def test_logout_success(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)
    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {tokens['access_token']}"},
    )
    assert resp.status_code == 204


def test_logout_all_success(client, mock_db_and_redis):
    tokens = _login(client, mock_db_and_redis)
    resp = client.post(
        "/api/v1/auth/logout-all",
        headers={"Authorization": f"Bearer {tokens['access_token']}"},
    )
    assert resp.status_code == 204
