from typing import Optional
from pydantic import BaseModel, Field
from shared.schemas import Role, ImpactLevel, TipCategory, MatchResult


class PerformanceMetric(BaseModel):
    value: float
    tierAverage: float
    percentile: float = Field(..., ge=0.0, le=100.0)


class PlayerPerformanceReport(BaseModel):
    summonerId: str
    matchesAnalyzed: int
    champion: Optional[str] = None
    role: Optional[Role] = None
    winRate: float
    kda: Optional[PerformanceMetric] = None
    avgDamageDealt: Optional[PerformanceMetric] = None
    avgDamageTaken: Optional[PerformanceMetric] = None
    avgVisionScore: Optional[PerformanceMetric] = None
    avgGoldPerMinute: Optional[PerformanceMetric] = None
    objectiveParticipation: Optional[PerformanceMetric] = None
    modelVersion: Optional[str] = None


class ImprovementTip(BaseModel):
    category: TipCategory
    tip: str
    impact: ImpactLevel
    relatedMetric: Optional[str] = None


class ImprovementTips(BaseModel):
    summonerId: str
    matchesAnalyzed: int
    tips: list[ImprovementTip]


class ParticipantStats(BaseModel):
    summonerId: str
    summonerName: str
    champion: str
    role: Role
    kills: int
    deaths: int
    assists: int
    kda: float
    damageDealt: int
    damageTaken: int
    goldEarned: int
    visionScore: int
    items: list[str]
    spells: list[str]


class MatchPerformanceReport(BaseModel):
    summonerId: str
    matchId: str
    champion: str
    role: Role
    result: MatchResult
    stats: Optional[ParticipantStats] = None
    comparedToTeamAvg: dict[str, float] = Field(default_factory=dict)
    comparedToGameAvg: dict[str, float] = Field(default_factory=dict)
    highlights: list[str] = Field(default_factory=list)
    lowlights: list[str] = Field(default_factory=list)
    modelVersion: Optional[str] = None
