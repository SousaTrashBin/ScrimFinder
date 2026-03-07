from typing import Optional
from pydantic import BaseModel, Field
from shared.schemas import Role


class AlternativeItem(BaseModel):
    replaces: str = Field(..., example="Runaan's Hurricane")
    suggestedItem: str = Field(..., example="Wit's End")
    reason: str = Field(..., example="Better vs AP-heavy compositions")


class BuildAnalysisRequest(BaseModel):
    champion: str = Field(..., example="Jinx")
    role: Optional[Role] = None
    items: list[str] = Field(..., min_length=1, example=["Kraken Slayer", "Runaan's Hurricane", "Infinity Edge"])
    enemyComposition: Optional[list[str]] = Field(None, example=["Malphite", "Galio", "Leona"])


class BuildAnalysisResult(BaseModel):
    champion: str
    items: list[str]
    score: int = Field(..., ge=0, le=100, description="Effectiveness score out of 100.", example=78)
    strengths: list[str] = Field(default_factory=list)
    weaknesses: list[str] = Field(default_factory=list)
    alternativeItems: list[AlternativeItem] = Field(default_factory=list)
    winRateWithBuild: Optional[float] = Field(None, example=53.1)
    modelVersion: Optional[str] = None


class BuildRecommendation(BaseModel):
    rank: int = Field(..., example=1)
    items: list[str]
    winRate: float = Field(..., example=55.2)
    pickRate: float = Field(..., example=38.1)
    sampleSize: int = Field(..., example=12400)
    notes: Optional[str] = None