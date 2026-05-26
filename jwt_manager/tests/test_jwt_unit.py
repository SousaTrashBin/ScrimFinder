"""
jwt_manager/tests/test_unit.py  —  Unit tests (no DB required)

All DB calls are routed through BQMock. These run in CI even when
no BigQuery service is present.

Run:
    pytest jwt_manager/tests/test_unit.py -v
"""

import pytest

from jwt_manager.core import security
from jwt_manager.core.schemas import RegisterRequest

pytestmark = pytest.mark.unit


def test_password_hash_is_verifiable_and_not_plaintext():
    hashed = security.hash_password("Password1!")
    assert hashed != "Password1!"
    assert security.verify_password("Password1!", hashed)
    assert not security.verify_password("wrong-password", hashed)


def test_access_token_contains_required_claims():
    token, jti, expires_at = security.create_access_token("user-1", "rodrigo")
    payload = security.decode_token(token)
    assert payload["sub"] == "user-1"
    assert payload["username"] == "rodrigo"
    assert payload["jti"] == jti
    assert payload["type"] == "access"
    assert expires_at is not None


def test_refresh_token_contains_required_claims():
    token, jti, expires_at = security.create_refresh_token("user-1")
    payload = security.decode_token(token)
    assert payload["sub"] == "user-1"
    assert payload["jti"] == jti
    assert payload["type"] == "refresh"
    assert expires_at is not None


def test_register_request_normalizes_username():
    request = RegisterRequest(
        username="  player  ", email="player@example.com", password="Password1!"
    )
    assert request.username == "player"


def test_register_request_rejects_weak_password():
    with pytest.raises(ValueError):
        RegisterRequest(
            username="ok-user", email="player@example.com", password="short"
        )


def test_jwks_has_rsa_key():
    jwks = security.build_jwks()
    assert "keys" in jwks
    assert len(jwks["keys"]) >= 1
    assert jwks["keys"][0]["kty"] == "RSA"
    assert jwks["keys"][0]["alg"] == "RS256"