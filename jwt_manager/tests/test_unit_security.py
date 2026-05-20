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


def test_register_request_normalizes_username_and_rejects_weak_password():
    request = RegisterRequest(
        username="  player  ", email="player@example.com", password="Password1!"
    )

    assert request.username == "player"
    with pytest.raises(ValueError):
        RegisterRequest(
            username="ok-user", email="player@example.com", password="short"
        )
