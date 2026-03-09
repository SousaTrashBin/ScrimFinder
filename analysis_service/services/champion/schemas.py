"""
services/champion/schemas.py
Pydantic models for the Champion Analysis Service.
"""

from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field


class Tier(str, Enum):
    IRON         = "IRON"
    BRONZE       = "BRONZE"
    SILVER       = "SILVER"
    GOLD         = "GOLD"
    PLATINUM     = "PLATINUM"
    EMERALD      = "EMERALD"
    DIAMOND      = "DIAMOND"
    MASTER       = "MASTER"
    GRANDMASTER  = "GRANDMASTER"
    CHALLENGER   = "CHALLENGER"


class Position(str, Enum):
    TOP     = "TOP"
    JUNGLE  = "JUNGLE"
    MIDDLE  = "MIDDLE"
    BOTTOM  = "BOTTOM"
    UTILITY = "UTILITY"


class SortBy(str, Enum):
    WIN_RATE   = "winRate"
    PICK_RATE  = "pickRate"
    AVG_KDA    = "avgKda"
    AVG_DAMAGE = "avgDamage"


# ── Champion win rate ─────────────────────────────────────────

class ChampionWinRate(BaseModel):
    champion:   str            = Field(..., example="Jinx")
    position:   Optional[str] = Field(None, example="BOTTOM")
    tier:       Optional[Tier] = Field(None, example="DIAMOND")
    winRate:    float          = Field(..., example=52.3,  description="Win rate as percentage (0–100).")
    wins:       int            = Field(..., example=12400)
    losses:     int            = Field(..., example=11300)
    totalGames: int            = Field(..., example=23700)


class WinRateByTierEntry(BaseModel):
    tier:       Tier  = Field(..., example="GOLD")
    winRate:    float = Field(..., example=54.1)
    totalGames: int   = Field(..., example=3200)


class ChampionWinRateByRank(BaseModel):
    champion:  str                      = Field(..., example="Jinx")
    position:  Optional[str]           = Field(None, example="BOTTOM")
    breakdown: list[WinRateByTierEntry] = Field(..., description="One entry per tier, IRON → CHALLENGER.")


# ── Builds ────────────────────────────────────────────────────

class BuildEntry(BaseModel):
    items:      list[str] = Field(..., example=["Kraken Slayer", "Runaan's Hurricane", "Infinity Edge"])
    winRate:    float     = Field(..., example=55.2)
    pickRate:   float     = Field(..., example=18.4, description="% of champion's games this build appeared in.")
    totalGames: int       = Field(..., example=4200)


class ChampionBuildsResult(BaseModel):
    champion: str              = Field(..., example="Jinx")
    position: Optional[str]   = Field(None)
    tier:     Optional[Tier]  = Field(None)
    builds:   list[BuildEntry] = Field(...)


# ── Positions ─────────────────────────────────────────────────

class PositionEntry(BaseModel):
    position:   str   = Field(..., example="BOTTOM")
    winRate:    float = Field(..., example=52.3)
    pickRate:   float = Field(..., example=78.4, description="% of this champion's games in this position.")
    totalGames: int   = Field(..., example=18500)
    avgKda:     float = Field(..., example=3.2)
    avgDamage:  int   = Field(..., example=22000)


class ChampionPositionsResult(BaseModel):
    champion:  str               = Field(..., example="Jinx")
    positions: list[PositionEntry] = Field(..., description="Sorted by win rate descending.")


# ── Matchups ──────────────────────────────────────────────────

class MatchupEntry(BaseModel):
    opponent:   str   = Field(..., example="Caitlyn")
    winRate:    float = Field(..., example=52.3, description="Win rate of queried champion vs this opponent.")
    totalGames: int   = Field(..., example=1200)
    avgKda:     float = Field(..., example=3.1)


class ChampionMatchupsResult(BaseModel):
    champion: str               = Field(..., example="Jinx")
    position: Optional[str]    = Field(None)
    matchups: list[MatchupEntry] = Field(..., description="Best matchups first (highest win rate).")


# ── Stats ─────────────────────────────────────────────────────

class ChampionStatsResult(BaseModel):
    champion:       str          = Field(..., example="Jinx")
    position:       Optional[str] = Field(None)
    tier:           Optional[Tier] = Field(None)
    totalGames:     int          = Field(..., example=23700)
    avgKills:       float        = Field(..., example=7.2)
    avgDeaths:      float        = Field(..., example=4.1)
    avgAssists:     float        = Field(..., example=6.3)
    avgKda:         float        = Field(..., example=3.2)
    avgDamage:      float        = Field(..., example=22400.0)
    avgGold:        float        = Field(..., example=12800.0)
    avgCs:          float        = Field(..., example=198.4)
    avgVisionScore: float        = Field(..., example=19.1)
    avgKp:          float        = Field(..., example=0.62, description="Kill participation 0.0–1.0.")


# ── Meta ──────────────────────────────────────────────────────

class TopChampionEntry(BaseModel):
    rank:       int            = Field(..., example=1)
    champion:   str            = Field(..., example="Jinx")
    winRate:    float          = Field(..., example=54.2)
    pickRate:   float          = Field(..., example=12.3)
    avgKda:     float          = Field(..., example=3.4)
    avgDamage:  int            = Field(..., example=23100)
    totalGames: int            = Field(..., example=18200)


class TopChampionsResult(BaseModel):
    position:  Optional[str]         = Field(None)
    tier:      Optional[Tier]        = Field(None)
    sortedBy:  SortBy                = Field(..., example="winRate")
    champions: list[TopChampionEntry] = Field(...)


class TierMetaEntry(BaseModel):
    tier:         Tier                   = Field(..., example="DIAMOND")
    topChampions: list[TopChampionEntry] = Field(...)


class MetaByRankResult(BaseModel):
    position: Optional[str]      = Field(None)
    tiers:    list[TierMetaEntry] = Field(..., description="One entry per tier, IRON → CHALLENGER.")