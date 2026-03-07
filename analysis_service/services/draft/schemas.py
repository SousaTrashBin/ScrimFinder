from typing import Optional
from pydantic import BaseModel, Field
from shared.schemas import Role, ErrorResponse


class ChampionInDraft(BaseModel):
    name: str = Field(..., example="Jinx")
    role: Role


class DraftTeam(BaseModel):
    champions: list[ChampionInDraft] = Field(..., min_length=1, max_length=5)


class DraftAnalysisRequest(BaseModel):
    teamA: DraftTeam
    teamB: DraftTeam

    model_config = {
        "json_schema_extra": {
            "example": {
                "teamA": {"champions": [
                    {"name": "Malphite", "role": "TOP"},
                    {"name": "Amumu",    "role": "JUNGLE"},
                    {"name": "Orianna",  "role": "MID"},
                    {"name": "Jinx",     "role": "BOT"},
                    {"name": "Lulu",     "role": "SUPPORT"},
                ]},
                "teamB": {"champions": [
                    {"name": "Fiora",   "role": "TOP"},
                    {"name": "Vi",      "role": "JUNGLE"},
                    {"name": "Zed",     "role": "MID"},
                    {"name": "Caitlyn", "role": "BOT"},
                    {"name": "Thresh",  "role": "SUPPORT"},
                ]},
            }
        }
    }


class DraftAnalysisResult(BaseModel):
    teamAWinProbability: float = Field(..., ge=0.0, le=1.0, example=0.57)
    teamBWinProbability: float = Field(..., ge=0.0, le=1.0, example=0.43)
    teamASynergies: list[str] = Field(default_factory=list)
    teamBSynergies: list[str] = Field(default_factory=list)
    teamACounters: list[str] = Field(default_factory=list)
    teamBCounters: list[str] = Field(default_factory=list)
    winConditions: dict[str, list[str]] = Field(default_factory=dict)
    tips: list[str] = Field(default_factory=list)
    modelVersion: Optional[str] = Field(None, description="Registry version of the model used.")


class ChampionMatchup(BaseModel):
    champion: str = Field(..., example="Jinx")
    opponent: str = Field(..., example="Caitlyn")
    role: Optional[Role] = None
    winRate: float = Field(..., example=52.3)
    sampleSize: int = Field(..., example=4820)
    avgKdaVsOpponent: float = Field(..., example=3.1)
    tips: list[str] = Field(default_factory=list)