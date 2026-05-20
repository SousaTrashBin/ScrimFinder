from typing import Annotated

import jwt as pyjwt
from fastapi import APIRouter, Header, HTTPException, Response, status

from jwt_manager.core import db, security
from jwt_manager.core.config import cfg
from jwt_manager.core.schemas import (
    LoginRequest,
    LoginResponse,
    RefreshRequest,
    RefreshResponse,
    RegisterRequest,
    RegisterResponse,
    ValidateResponse,
)

router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])


def _bearer_token(authorization: str) -> str:
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(
            status_code=401, detail="Authorization header must be 'Bearer <token>'."
        )
    return token


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


def _current_user_from_access(authorization: str) -> tuple[dict, dict]:
    payload = _decode_token(_bearer_token(authorization), "access")
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
    try:
        user = db.create_user(
            username=username, email=email, password_hash=password_hash
        )
    except Exception as exc:
        raise HTTPException(status_code=409, detail="User already exists.") from exc

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

    access_token, access_jti, _ = security.create_access_token(
        str(user["id"]), user["username"]
    )
    refresh_token, refresh_jti, refresh_exp = security.create_refresh_token(
        str(user["id"])
    )

    db.cache_access_token(access_jti, str(user["id"]), cfg.ACCESS_TOKEN_TTL)
    db.store_refresh_token(refresh_jti, str(user["id"]), refresh_exp)
    return LoginResponse(access_token=access_token, refresh_token=refresh_token)


@router.get("/validate", response_model=ValidateResponse)
def validate(authorization: Annotated[str, Header()]) -> ValidateResponse:
    _, user = _current_user_from_access(authorization)
    return ValidateResponse(user_id=str(user["id"]), username=user["username"])


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

    db.revoke_refresh_token(payload["jti"])
    access_token, access_jti, _ = security.create_access_token(
        user_id, user["username"]
    )
    refresh_token, refresh_jti, refresh_exp = security.create_refresh_token(user_id)
    db.cache_access_token(access_jti, user_id, cfg.ACCESS_TOKEN_TTL)
    db.store_refresh_token(refresh_jti, user_id, refresh_exp)

    return RefreshResponse(access_token=access_token, refresh_token=refresh_token)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(authorization: Annotated[str, Header()]) -> Response:
    payload, _ = _current_user_from_access(authorization)
    db.invalidate_access_token(payload["jti"])
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/logout-all", status_code=status.HTTP_204_NO_CONTENT)
def logout_all(authorization: Annotated[str, Header()]) -> Response:
    _, user = _current_user_from_access(authorization)
    user_id = str(user["id"])
    db.invalidate_all_user_sessions(user_id)
    db.revoke_all_user_tokens(user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/jwks.json")
def jwks() -> dict:
    return security.build_jwks()


@router.get("/public-key", response_class=Response)
def public_key() -> Response:
    return Response(content=security.get_public_key_pem(), media_type="text/plain")
