"""
shared/schemas.py
Pydantic types shared across all services and the training pipeline.
"""
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field


class Role(str, Enum):
    TOP = "TOP"
    JUNGLE = "JUNGLE"
    MID = "MID"
    BOT = "BOT"
    SUPPORT = "SUPPORT"


class ImpactLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class TipCategory(str, Enum):
    VISION = "VISION"
    FARMING = "FARMING"
    DAMAGE = "DAMAGE"
    POSITIONING = "POSITIONING"
    OBJECTIVE_CONTROL = "OBJECTIVE_CONTROL"
    BUILDS = "BUILDS"
    GAME_SENSE = "GAME_SENSE"


class MatchResult(str, Enum):
    WIN = "WIN"
    LOSS = "LOSS"


class ErrorResponse(BaseModel):
    code: int = Field(..., example=404)
    message: str = Field(..., example="Resource not found.")
    details: Optional[str] = Field(None, example="No player found with summonerId 'xyz'.")