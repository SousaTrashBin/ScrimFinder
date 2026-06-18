import jwt as pyjwt
from fastapi import APIRouter, HTTPException, Response, Security, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from ..core import db, security
from ..core.config import cfg
from ..core.schemas import (
    LoginRequest,
    LoginResponse,
    RefreshRequest,
    RefreshResponse,
    RegisterRequest,
    RegisterResponse,
    ValidateResponse,
)

router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])

security_bearer = HTTPBearer()


def _decode_token(token: str, expected_type: str) -> dict:
    try:
        payload = security.decode_token(token)
    except pyjwt.ExpiredSignatureError as exc:
        raise HTTPException(status_code=401, detail="Token has expired.") from exc
    except pyjwt.InvalidTokenError as exc:
        raise HTTPException(status_code=401, detail="Invalid token.") from exc

    if payload.get("type") != expected_type:
        raise HTTPException(
            status_code=401, detail=f"Expected a {expected_type} token."
        )
    return payload


def _current_user_from_access(
    credentials: HTTPAuthorizationCredentials = Security(security_bearer),
) -> tuple[dict, dict]:
    """Dependency: validates the access token and returns (payload, user)."""
    token = credentials.credentials
    payload = _decode_token(token, "access")
    user_id = str(payload["sub"])
    jti = payload["jti"]

    cached_user_id = db.get_cached_session(jti)
    if cached_user_id is None or str(cached_user_id) != user_id:
        raise HTTPException(status_code=401, detail="Session is no longer active.")

    user = db.get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="User no longer exists.")
    if not user.get("is_active", True):
        raise HTTPException(status_code=403, detail="User is inactive.")
    return payload, user


# ── Public endpoints ──────────────────────────────────────────────────────────


@router.post(
    "/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED
)
def register(body: RegisterRequest) -> RegisterResponse:
    username = body.username.strip()
    email = str(body.email).lower()

    if db.get_user_by_username(username):
        raise HTTPException(status_code=409, detail="Username already exists.")
    if db.get_user_by_email(email):
        raise HTTPException(status_code=409, detail="Email already exists.")

    password_hash = security.hash_password(body.password)
    user = db.create_user(username=username, email=email, password_hash=password_hash)

    return RegisterResponse(
        id=str(user["id"]), username=user["username"], email=user["email"]
    )


@router.post("/login", response_model=LoginResponse)
def login(body: LoginRequest) -> LoginResponse:
    user = db.get_user_by_username(body.username)
    if user is None or not security.verify_password(
        body.password, user["password_hash"]
    ):
        raise HTTPException(status_code=401, detail="Invalid username or password.")
    if not user.get("is_active", True):
        raise HTTPException(status_code=403, detail="User is inactive.")

    user_id = str(user["id"])

    # ── SINGLE SESSION: nuke everything before issuing new tokens ─────────────
    db.invalidate_all_user_sessions(user_id)
    db.revoke_all_user_tokens(user_id)

    access_token, access_jti, _ = security.create_access_token(
        user_id, user["username"]
    )
    refresh_token, refresh_jti, refresh_exp = security.create_refresh_token(user_id)

    db.cache_access_token(access_jti, user_id, cfg.ACCESS_TOKEN_TTL)
    db.store_refresh_token(refresh_jti, user_id, refresh_exp)
    return LoginResponse(access_token=access_token, refresh_token=refresh_token)


@router.post("/refresh", response_model=RefreshResponse)
def refresh(body: RefreshRequest) -> RefreshResponse:
    payload = _decode_token(body.refresh_token, "refresh")
    token_row = db.get_refresh_token(payload["jti"])
    if token_row is None or token_row.get("revoked"):
        raise HTTPException(status_code=401, detail="Refresh token is not active.")

    user_id = str(token_row.get("user_id") or payload["sub"])
    if user_id != str(payload["sub"]):
        raise HTTPException(status_code=401, detail="Refresh token subject mismatch.")

    user = db.get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="User no longer exists.")
    if not user.get("is_active", True):
        raise HTTPException(status_code=403, detail="User is inactive.")

    # ── SINGLE SESSION: revoke old refresh + nuke all sessions/tokens ───────
    db.revoke_refresh_token(payload["jti"])
    db.invalidate_all_user_sessions(user_id)
    db.revoke_all_user_tokens(user_id)

    access_token, access_jti, _ = security.create_access_token(
        user_id, user["username"]
    )
    refresh_token, refresh_jti, refresh_exp = security.create_refresh_token(user_id)
    db.cache_access_token(access_jti, user_id, cfg.ACCESS_TOKEN_TTL)
    db.store_refresh_token(refresh_jti, user_id, refresh_exp)

    return RefreshResponse(access_token=access_token, refresh_token=refresh_token)


@router.get("/jwks.json")
def jwks() -> dict:
    return security.build_jwks()


@router.get("/public-key", response_class=Response)
def public_key() -> Response:
    return Response(content=security.get_public_key_pem(), media_type="text/plain")


# ── Protected endpoints ───────────────────────────────────────────────────────


@router.get("/validate", response_model=ValidateResponse)
def validate(
    user_data: tuple = Security(_current_user_from_access),
) -> ValidateResponse:
    _, user = user_data
    return ValidateResponse(user_id=str(user["id"]), username=user["username"])


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(
    user_data: tuple = Security(_current_user_from_access),
) -> Response:
    payload, _ = user_data
    db.invalidate_access_token(payload["jti"])
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/logout-all", status_code=status.HTTP_204_NO_CONTENT)
def logout_all(
    user_data: tuple = Security(_current_user_from_access),
) -> Response:
    _, user = user_data
    user_id = str(user["id"])
    db.invalidate_all_user_sessions(user_id)
    db.revoke_all_user_tokens(user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
