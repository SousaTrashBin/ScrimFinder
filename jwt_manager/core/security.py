"""
Password hashing (bcrypt) and JWT creation/validation with RS256.
"""

import base64
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Optional, Tuple

import bcrypt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
import jwt as pyjwt

from jwt_manager.core.config import cfg


# ── Lazy RSA key loading (does NOT run at import time) ───────────────────────

_PRIVATE_PEM: Optional[bytes] = None
_PUBLIC_PEM: Optional[bytes] = None
_PUBLIC_KEY_OBJ: Optional[Any] = None
_KEY_ID: Optional[str] = None


def _generate_keypair() -> Tuple[bytes, bytes, Any]:
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


def init_keys() -> None:
    """Call once at startup (after BQ tables exist) to load or generate keys."""
    global _PRIVATE_PEM, _PUBLIC_PEM, _PUBLIC_KEY_OBJ, _KEY_ID

    if _PRIVATE_PEM is not None:
        return

    priv_pem_str = cfg.JWT_PRIVATE_KEY_PEM
    pub_pem_str = cfg.JWT_PUBLIC_KEY_PEM

    if priv_pem_str and pub_pem_str:
        private_key = serialization.load_pem_private_key(
            priv_pem_str.encode(), password=None
        )
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
    else:
        # Dev fallback — throwaway pair
        private_pem, public_pem, public_key = _generate_keypair()

    _PRIVATE_PEM = private_pem
    _PUBLIC_PEM = public_pem
    _PUBLIC_KEY_OBJ = public_key
    _KEY_ID = cfg.JWT_KEY_ID


def _ensure_keys() -> None:
    if _PRIVATE_PEM is None:
        init_keys()


# ── JWKS ──────────────────────────────────────────────────────────────────────


def _b64url_int(n: int) -> str:
    byte_len = (n.bit_length() + 7) // 8
    return base64.urlsafe_b64encode(n.to_bytes(byte_len, "big")).rstrip(b"=").decode()


def build_jwks() -> dict:
    _ensure_keys()
    pub_numbers = _PUBLIC_KEY_OBJ.public_numbers()
    return {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "RS256",
                "kid": _KEY_ID,
                "n": _b64url_int(pub_numbers.n),
                "e": _b64url_int(pub_numbers.e),
            }
        ]
    }


def get_public_key_pem() -> bytes:
    _ensure_keys()
    return _PUBLIC_PEM


# ── Passwords ─────────────────────────────────────────────────────────────────


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt()).decode()


def verify_password(plain: str, hashed: str) -> bool:
    return bcrypt.checkpw(plain.encode(), hashed.encode())


# ── Token creation ────────────────────────────────────────────────────────────


def _now() -> datetime:
    return datetime.now(timezone.utc)


def create_access_token(user_id: str, username: str) -> Tuple[str, str, datetime]:
    _ensure_keys()
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
        payload, _PRIVATE_PEM, algorithm="RS256", headers={"kid": _KEY_ID}
    )
    return token, jti, exp


def create_refresh_token(user_id: str) -> Tuple[str, str, datetime]:
    _ensure_keys()
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
        payload, _PRIVATE_PEM, algorithm="RS256", headers={"kid": _KEY_ID}
    )
    return token, jti, exp


# ── Token validation ──────────────────────────────────────────────────────────


def decode_token(token: str) -> dict:
    _ensure_keys()
    return pyjwt.decode(
        token,
        _PUBLIC_PEM,
        algorithms=["RS256"],
        issuer=cfg.ISSUER,          # ← verifies the token came from us
        options={"require": ["sub", "jti", "exp", "iss"]},
    )