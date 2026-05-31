"""
jwt_manager/tests/test_jwt_acceptance.py  —  Acceptance / contract tests

Validates that every public auth endpoint responds correctly for both
happy-path and expected-error scenarios. Uses BQMock so no live DB needed.

Run:
    pytest jwt_manager/tests/test_jwt_acceptance.py -v
"""

import pytest
from fastapi.testclient import TestClient

from .jwt_bq_mock import BQMock

pytestmark = pytest.mark.acceptance


@pytest.fixture
def client(monkeypatch):
    from ..main import app
    from ..core import db

    BQMock(monkeypatch)
    # Seed a test user
    db.create_user("testuser", "test@scrimfinder.gg", "")

    # Prevent init_db from trying to create real tables
    monkeypatch.setattr("jwt_manager.core.db.init_db", lambda: None)

    return TestClient(app, raise_server_exceptions=False)


@pytest.fixture
def logged_in_client(client):
    """Returns (client, tokens) tuple with a logged-in session."""
    from ..core import security
    from ..core import db
    from ..core import db as db_mod

    db.get_user_by_username("testuser")
    hashed = security.hash_password("Password1!")

    original_get = db_mod.get_user_by_username

    def patched_get(username):
        u = original_get(username)
        if u and username == "testuser":
            u["password_hash"] = hashed
        return u

    from ..routers import auth as auth_mod

    auth_mod.db.get_user_by_username = patched_get

    r = client.post(
        "/api/v1/auth/login", json={"username": "testuser", "password": "Password1!"}
    )
    assert r.status_code == 200
    return client, r.json()


# ── System ────────────────────────────────────────────────────────────────────


def test_health_and_jwks_contract(client):
    assert client.get("/api/v1/auth/health").json() == {"status": "ok"}
    jwks = client.get("/api/v1/auth/jwks.json").json()
    assert jwks["keys"][0]["kty"] == "RSA"
    assert jwks["keys"][0]["alg"] == "RS256"


def test_openapi_exposes_auth_flow(client):
    paths = client.get("/api/v1/auth/openapi.json").json()["paths"]
    for path in (
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/validate",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/logout-all",
    ):
        assert path in paths, f"Missing {path}"


# ── Register ──────────────────────────────────────────────────────────────────


class TestRegisterAcceptance:
    def test_success(self, client):
        r = client.post(
            "/api/v1/auth/register",
            json={
                "username": "newuser",
                "email": "new@scrimfinder.gg",
                "password": "SecurePass1!",
            },
        )
        assert r.status_code == 201
        body = r.json()
        assert "id" in body
        assert body["username"] == "newuser"

    def test_duplicate_username(self, client):
        r = client.post(
            "/api/v1/auth/register",
            json={
                "username": "testuser",
                "email": "other@scrimfinder.gg",
                "password": "SecurePass1!",
            },
        )
        assert r.status_code == 409

    def test_weak_password(self, client):
        r = client.post(
            "/api/v1/auth/register",
            json={
                "username": "weakuser",
                "email": "weak@scrimfinder.gg",
                "password": "short",
            },
        )
        assert r.status_code == 422

    def test_short_username(self, client):
        r = client.post(
            "/api/v1/auth/register",
            json={
                "username": "ab",
                "email": "ab@scrimfinder.gg",
                "password": "SecurePass1!",
            },
        )
        assert r.status_code == 422


# ── Login ─────────────────────────────────────────────────────────────────────


class TestLoginAcceptance:
    def test_success(self, logged_in_client):
        client, tokens = logged_in_client
        assert "access_token" in tokens
        assert "refresh_token" in tokens

    def test_wrong_password(self, client):
        r = client.post(
            "/api/v1/auth/login",
            json={"username": "testuser", "password": "WrongPass!"},
        )
        assert r.status_code == 401

    def test_unknown_user(self, client):
        r = client.post(
            "/api/v1/auth/login",
            json={"username": "nobody", "password": "Password1!"},
        )
        assert r.status_code == 401


# ── Single Session ───────────────────────────────────────────────────────────


class TestSingleSessionAcceptance:
    def test_second_login_invalidates_first(self, logged_in_client):
        client, tokens1 = logged_in_client

        # Login again
        r = client.post(
            "/api/v1/auth/login",
            json={"username": "testuser", "password": "Password1!"},
        )
        assert r.status_code == 200
        tokens2 = r.json()

        # First access token should be dead
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": f"Bearer {tokens1['access_token']}"},
        )
        assert r.status_code == 401
        assert "Session is no longer active" in r.json()["detail"]

        # Second access token should work
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": f"Bearer {tokens2['access_token']}"},
        )
        assert r.status_code == 200

    def test_refresh_invalidates_old_access_token(self, logged_in_client):
        client, tokens = logged_in_client

        r = client.post(
            "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
        )
        assert r.status_code == 200
        new_tokens = r.json()

        # Old access token should be dead
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
        )
        assert r.status_code == 401

        # New access token should work
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": f"Bearer {new_tokens['access_token']}"},
        )
        assert r.status_code == 200

    def test_refresh_revokes_old_refresh_token(self, logged_in_client):
        client, tokens = logged_in_client

        r = client.post(
            "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
        )
        assert r.status_code == 200

        # Re-using the old refresh token should fail
        r = client.post(
            "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
        )
        assert r.status_code == 401


# ── Validate ──────────────────────────────────────────────────────────────────


class TestValidateAcceptance:
    def test_success(self, logged_in_client):
        client, tokens = logged_in_client
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
        )
        assert r.status_code == 200
        assert r.json()["username"] == "testuser"

    def test_missing_header(self, client):
        """HTTPBearer returns 401 when Authorization header is absent."""
        assert client.get("/api/v1/auth/validate").status_code == 401

    def test_invalid_token(self, client):
        r = client.get(
            "/api/v1/auth/validate",
            headers={"Authorization": "Bearer this.is.garbage"},
        )
        assert r.status_code == 401


# ── Refresh ───────────────────────────────────────────────────────────────────


class TestRefreshAcceptance:
    def test_success(self, logged_in_client):
        client, tokens = logged_in_client
        r = client.post(
            "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
        )
        assert r.status_code == 200
        new_tokens = r.json()
        assert "access_token" in new_tokens
        assert "refresh_token" in new_tokens

    def test_with_access_token(self, logged_in_client):
        client, tokens = logged_in_client
        r = client.post(
            "/api/v1/auth/refresh", json={"refresh_token": tokens["access_token"]}
        )
        assert r.status_code == 401


# ── Logout ────────────────────────────────────────────────────────────────────


class TestLogoutAcceptance:
    def test_success(self, logged_in_client):
        client, tokens = logged_in_client
        r = client.post(
            "/api/v1/auth/logout",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
        )
        assert r.status_code == 204

    def test_logout_all(self, logged_in_client):
        client, tokens = logged_in_client
        r = client.post(
            "/api/v1/auth/logout-all",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
        )
        assert r.status_code == 204
