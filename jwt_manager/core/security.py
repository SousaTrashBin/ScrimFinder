"""
Password hashing (bcrypt) and JWT creation/validation with RS256.

Why RS256 instead of HS256
--------------------------
Istio RequestAuthentication validates tokens by fetching a public key from the
JWKS endpoint. With HS256 every service would need the shared secret — one leak
compromises the whole platform. RS256 means only this service ever touches the
private key; all other consumers verify with the public key from /jwks.json.

Key management
--------------
  Production:
    Set JWT_PRIVATE_KEY and JWT_PUBLIC_KEY env vars to PEM strings (inject via
    K8s secret). Use the helper script below to generate a key pair once:

      openssl genrsa -out private.pem 2048
      openssl rsa -in private.pem -pubout -out public.pem

    Then store the contents in the secret and never commit to git.

  Development / tests:
    Leave both env vars unset. A throwaway in-memory key pair is generated at
    startup. Tokens are valid only for the lifetime of that process — fine for
    local dev and unit tests that mock the DB anyway.
"""

import base64
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import bcrypt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
import jwt as pyjwt

from jwt_manager.core.config import cfg


# ── RSA key pair (loaded once at import time) ─────────────────────────────────


def _load_or_generate() -> tuple[bytes, bytes, Any]:
    priv_pem_str = cfg.JWT_PRIVATE_KEY_PEM
    pub_pem_str = cfg.JWT_PUBLIC_KEY_PEM

    if priv_pem_str and pub_pem_str:
        private_key = serialization.load_pem_private_key(
            priv_pem_str.encode(), password=None
        )
    else:
        # Dev fallback — throwaway pair, never survives a restart
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    public_key = private_key.public_key()

    private_pem = private_key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption(),
    )
    public_pem = public_key.public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return private_pem, public_pem, public_key


_PRIVATE_PEM, _PUBLIC_PEM, _PUBLIC_KEY_OBJ = _load_or_generate()
KEY_ID = cfg.JWT_KEY_ID


# ── JWKS ──────────────────────────────────────────────────────────────────────


def _b64url_int(n: int) -> str:
    """Encode an RSA big integer (modulus/exponent) as base64url, no padding."""
    byte_len = (n.bit_length() + 7) // 8
    return base64.urlsafe_b64encode(n.to_bytes(byte_len, "big")).rstrip(b"=").decode()


def build_jwks() -> dict:
    """
    Build the JWK Set document for the current public key.

    Istio's RequestAuthentication fetches this from jwksUri on startup and on
    every 401 it encounters with an unrecognised kid. The endpoint is:
        GET /api/v1/auth/jwks.json
    """
    pub_numbers = _PUBLIC_KEY_OBJ.public_numbers()
    return {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "RS256",
                "kid": KEY_ID,
                "n": _b64url_int(pub_numbers.n),
                "e": _b64url_int(pub_numbers.e),
            }
        ]
    }


def get_public_key_pem() -> bytes:
    """Raw PEM bytes of the public key — exposed at /public-key for debugging."""
    return _PUBLIC_PEM


# ── Passwords ─────────────────────────────────────────────────────────────────


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt()).decode()


def verify_password(plain: str, hashed: str) -> bool:
    return bcrypt.checkpw(plain.encode(), hashed.encode())


# ── Token creation ────────────────────────────────────────────────────────────


def _now() -> datetime:
    return datetime.now(timezone.utc)


def create_access_token(user_id: str, username: str) -> tuple[str, str, datetime]:
    """Returns (encoded_token, jti, expires_at)."""
    jti = str(uuid.uuid4())
    exp = _now() + timedelta(seconds=cfg.ACCESS_TOKEN_TTL)
    payload = {
        "sub": str(user_id),
        "username": username,
        "jti": jti,
        "iss": cfg.ISSUER,
        "iat": _now(),
        "exp": exp,
        "type": "access",
    }
    token = pyjwt.encode(
        payload, _PRIVATE_PEM, algorithm="RS256", headers={"kid": KEY_ID}
    )
    return token, jti, exp


def create_refresh_token(user_id: str) -> tuple[str, str, datetime]:
    """Returns (encoded_token, jti, expires_at)."""
    jti = str(uuid.uuid4())
    exp = _now() + timedelta(seconds=cfg.REFRESH_TOKEN_TTL)
    payload = {
        "sub": str(user_id),
        "jti": jti,
        "iss": cfg.ISSUER,
        "iat": _now(),
        "exp": exp,
        "type": "refresh",
    }
    token = pyjwt.encode(
        payload, _PRIVATE_PEM, algorithm="RS256", headers={"kid": KEY_ID}
    )
    return token, jti, exp


# ── Token validation ──────────────────────────────────────────────────────────


def decode_token(token: str) -> dict:
    """
    Verify signature with the RSA public key.
    Raises:
        jwt.ExpiredSignatureError  — token past its exp
        jwt.InvalidTokenError      — bad signature, missing claims, etc.
    """
    return pyjwt.decode(
        token,
        _PUBLIC_PEM,
        algorithms=["RS256"],
        options={"require": ["sub", "jti", "exp", "iss"]},
    )
