from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, Field


class Role(StrEnum):
    TOP = "TOP"
    JUNGLE = "JUNGLE"
    MID = "MID"
    BOT = "BOT"
    SUPPORT = "SUPPORT"


class ImpactLevel(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class TipCategory(StrEnum):
    VISION = "VISION"
    FARMING = "FARMING"
    DAMAGE = "DAMAGE"
    POSITIONING = "POSITIONING"
    OBJECTIVE_CONTROL = "OBJECTIVE_CONTROL"
    BUILDS = "BUILDS"
    GAME_SENSE = "GAME_SENSE"


class Concern(StrEnum):
    DRAFT = "draft"
    BUILD = "build"
    PERFORMANCE = "performance"


class ErrorResponse(BaseModel):
    code: int
    message: str
    details: str | None = None


class ChampionInDraft(BaseModel):
    name: str
    role: Role


class DraftTeam(BaseModel):
    champions: list[ChampionInDraft] = Field(..., min_length=1, max_length=5)


class DraftAnalysisRequest(BaseModel):
    team_blue: DraftTeam
    team_red: DraftTeam
    model_id: int | None = None


class DraftAnalysisResponse(BaseModel):
    blue_win_probability: float = Field(..., ge=0, le=1)
    red_win_probability: float = Field(..., ge=0, le=1)
    blue_synergies: list[str]
    red_synergies: list[str]
    blue_counters: list[str]
    red_counters: list[str]
    win_conditions: dict
    tips: list[str]
    model_version: str | None


class BuildAnalysisRequest(BaseModel):
    champion: str
    role: Role | None = None
    items: list[str] = Field(..., min_length=1)
    enemy_composition: list[str] | None = None
    model_id: int | None = None


class AlternativeItem(BaseModel):
    replaces: str
    suggested_item: str
    reason: str


class BuildAnalysisResponse(BaseModel):
    champion: str
    items: list[str]
    score: int = Field(..., ge=0, le=100)
    win_rate_with_build: float | None
    strengths: list[str]
    weaknesses: list[str]
    alternative_items: list[AlternativeItem]
    model_version: str | None


class PlayerAnalysisRequest(BaseModel):
    summoner_id: str
    champion: str | None = None
    role: Role | None = None
    last_n_games: int = Field(20, ge=1, le=200)
    match_type: str | None = None


class PerformanceMetric(BaseModel):
    value: float
    tier_average: float
    percentile: float = Field(..., ge=0, le=100)


class ImprovementTip(BaseModel):
    category: TipCategory
    tip: str
    impact: ImpactLevel
    related_metric: str | None = None


class PlayerAnalysisResponse(BaseModel):
    summoner_id: str
    matches_analyzed: int
    champion: str | None
    role: str | None
    win_rate: float
    kda: PerformanceMetric | None
    avg_damage: PerformanceMetric | None
    avg_vision: PerformanceMetric | None
    avg_gold_pm: PerformanceMetric | None
    obj_participation: PerformanceMetric | None
    tips: list[ImprovementTip]
    model_version: str | None


class GameAnalysisRequest(BaseModel):
    game_id: str | None = None
    raw_data: dict | None = None
    model_id: int | None = None
    concern: Concern = Concern.PERFORMANCE


class PlayerOutcome(BaseModel):
    puuid: str
    champion: str
    role: str
    win: bool
    kda: float
    damage: int
    gold: int
    cs: int
    vision: int
    performance_score: float | None = None
    highlights: list[str] = []
    lowlights: list[str] = []


class GameAnalysisResponse(BaseModel):
    game_id: str
    patch: str | None
    duration_sec: int | None
    winner: str | None
    players: list[PlayerOutcome]
    team_synergies: dict
    key_moments: list[str]
    model_version: str | None


class ChampionAnalysisRequest(BaseModel):
    champion: str
    position: str | None = None
    patch: str | None = None
    match_type: str | None = None
    model_id: int | None = None


class ChampionStats(BaseModel):
    champion: str
    position: str | None
    win_rate: float
    pick_rate: float
    total_games: int
    avg_kda: float
    avg_damage: float
    avg_gold: float
    avg_cs: float
    avg_vision: float
    best_items: list[str]
    best_runes: list[str]
    counters: list[str]
    countered_by: list[str]


class ChampionAnalysisResponse(BaseModel):
    champion: str
    stats: ChampionStats
    tier: str
    model_version: str | None
