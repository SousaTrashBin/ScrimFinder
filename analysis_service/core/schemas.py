from __future__ import annotations
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field

class Role(str, Enum):
    TOP="TOP"; JUNGLE="JUNGLE"; MID="MID"; BOT="BOT"; SUPPORT="SUPPORT"

class ImpactLevel(str, Enum):
    LOW="LOW"; MEDIUM="MEDIUM"; HIGH="HIGH"

class TipCategory(str, Enum):
    VISION="VISION"; FARMING="FARMING"; DAMAGE="DAMAGE"; POSITIONING="POSITIONING"
    OBJECTIVE_CONTROL="OBJECTIVE_CONTROL"; BUILDS="BUILDS"; GAME_SENSE="GAME_SENSE"

class Concern(str, Enum):
    DRAFT="draft"; BUILD="build"; PERFORMANCE="performance"

class ErrorResponse(BaseModel):
    code: int; message: str; details: Optional[str] = None

class ChampionInDraft(BaseModel):
    name: str; role: Role

class DraftTeam(BaseModel):
    champions: list[ChampionInDraft] = Field(..., min_length=1, max_length=5)

class DraftAnalysisRequest(BaseModel):
    team_blue: DraftTeam; team_red: DraftTeam; model_id: Optional[int] = None

class DraftAnalysisResponse(BaseModel):
    blue_win_probability: float = Field(..., ge=0, le=1)
    red_win_probability: float = Field(..., ge=0, le=1)
    blue_synergies: list[str]; red_synergies: list[str]
    blue_counters: list[str]; red_counters: list[str]
    win_conditions: dict; tips: list[str]; model_version: Optional[str]

class BuildAnalysisRequest(BaseModel):
    champion: str; role: Optional[Role] = None
    items: list[str] = Field(..., min_length=1)
    enemy_composition: Optional[list[str]] = None
    model_id: Optional[int] = None

class AlternativeItem(BaseModel):
    replaces: str; suggested_item: str; reason: str

class BuildAnalysisResponse(BaseModel):
    champion: str; items: list[str]; score: int = Field(..., ge=0, le=100)
    win_rate_with_build: Optional[float]; strengths: list[str]
    weaknesses: list[str]; alternative_items: list[AlternativeItem]
    model_version: Optional[str]

class PlayerAnalysisRequest(BaseModel):
    summoner_id: str; champion: Optional[str] = None
    role: Optional[Role] = None
    last_n_games: int = Field(20, ge=1, le=200)
    match_type: Optional[str] = None

class PerformanceMetric(BaseModel):
    value: float; tier_average: float; percentile: float = Field(..., ge=0, le=100)

class ImprovementTip(BaseModel):
    category: TipCategory; tip: str; impact: ImpactLevel
    related_metric: Optional[str] = None

class PlayerAnalysisResponse(BaseModel):
    summoner_id: str; matches_analyzed: int; champion: Optional[str]
    role: Optional[str]; win_rate: float
    kda: Optional[PerformanceMetric]; avg_damage: Optional[PerformanceMetric]
    avg_vision: Optional[PerformanceMetric]; avg_gold_pm: Optional[PerformanceMetric]
    obj_participation: Optional[PerformanceMetric]
    tips: list[ImprovementTip]; model_version: Optional[str]

class GameAnalysisRequest(BaseModel):
    game_id: Optional[str] = None; raw_data: Optional[dict] = None
    model_id: Optional[int] = None; concern: Concern = Concern.PERFORMANCE

class PlayerOutcome(BaseModel):
    puuid: str; champion: str; role: str; win: bool; kda: float
    damage: int; gold: int; cs: int; vision: int
    performance_score: Optional[float] = None
    highlights: list[str] = []; lowlights: list[str] = []

class GameAnalysisResponse(BaseModel):
    game_id: str; patch: Optional[str]; duration_sec: Optional[int]
    winner: Optional[str]; players: list[PlayerOutcome]
    team_synergies: dict; key_moments: list[str]; model_version: Optional[str]

class ChampionAnalysisRequest(BaseModel):
    champion: str; position: Optional[str] = None; patch: Optional[str] = None
    match_type: Optional[str] = None; model_id: Optional[int] = None

class ChampionStats(BaseModel):
    champion: str; position: Optional[str]; win_rate: float; pick_rate: float
    total_games: int; avg_kda: float; avg_damage: float; avg_gold: float
    avg_cs: float; avg_vision: float; best_items: list[str]
    best_runes: list[str]; counters: list[str]; countered_by: list[str]

class ChampionAnalysisResponse(BaseModel):
    champion: str; stats: ChampionStats; tier: str; model_version: Optional[str]
