"""
services/draft/main.py  —  Draft Analysis Service  (port 8001)
"""

from fastapi import FastAPI, Query, HTTPException
from typing import Optional

from shared.schemas import ErrorResponse, Role
from services.draft.schemas import (
    DraftAnalysisRequest, DraftAnalysisResult, ChampionMatchup
)
from services.draft import model as draft_model

app = FastAPI(
    title="Draft Analysis Service",
    description=(
        "Analyses team draft compositions using a trained win-probability model.\n\n"
        "**Student:** Rodrigo Neto (fc59850)"
    ),
    version="1.0.0",
)


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok", "modelVersion": draft_model.current_version()}


@app.post(
    "/analysis/draft",
    response_model=DraftAnalysisResult,
    summary="Analyse a team draft composition",
    description=(
        "Accepts two team compositions (champion + role) and returns synergy analysis, "
        "counter matchups, win conditions, and an ML-derived win probability."
    ),
    tags=["Draft Analysis"],
    responses={400: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def analyze_draft(body: DraftAnalysisRequest) -> DraftAnalysisResult:
    try:
        # Build simple numeric features from champion names (stub: use hash % 1.0)
        # TODO: replace with real champion embedding / one-hot encoding
        def champ_features(team):
            return [hash(c.name) % 1000 / 1000.0 for c in team.champions]

        prob_a, prob_b = draft_model.predict_win_probability(
            champ_features(body.teamA),
            champ_features(body.teamB),
        )
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    return DraftAnalysisResult(
        teamAWinProbability=prob_a,
        teamBWinProbability=prob_b,
        teamASynergies=_synergies(body.teamA.champions),
        teamBSynergies=_synergies(body.teamB.champions),
        teamACounters=_counters(body.teamA.champions, body.teamB.champions),
        teamBCounters=_counters(body.teamB.champions, body.teamA.champions),
        winConditions={
            "teamA": ["Force early teamfights", "Leverage AoE ultimates"],
            "teamB": ["Split push", "Pick off isolated carries"],
        },
        tips=["Prioritise Dragon — favours AoE compositions"],
        modelVersion=draft_model.current_version(),
    )


@app.get(
    "/analysis/draft/matchup",
    response_model=ChampionMatchup,
    summary="Get head-to-head champion matchup data",
    description=(
        "Returns historical win rate and KDA for a champion vs opponent pairing, "
        "optionally scoped to a role. Derived from EUW high-elo data."
    ),
    tags=["Draft Analysis"],
    responses={400: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_matchup(
    champion: str = Query(..., example="Jinx"),
    opponent: str = Query(..., example="Caitlyn"),
    role: Optional[Role] = Query(None),
) -> ChampionMatchup:
    # TODO: query pre-computed matchup table from the EUW dataset
    return ChampionMatchup(
        champion=champion,
        opponent=opponent,
        role=role,
        winRate=52.3,
        sampleSize=4820,
        avgKdaVsOpponent=3.1,
        tips=["Play aggressive at level 2", "Avoid trading post-6"],
    )


# ── Helpers (stub rule-based analysis) ───────────────────────

def _synergies(champions) -> list[str]:
    names = [c.name for c in champions]
    synergies = []
    if "Amumu" in names and "Orianna" in names:
        synergies.append("Amumu + Orianna AoE wombo-combo")
    if "Jinx" in names and "Lulu" in names:
        synergies.append("Jinx + Lulu hypercarry protection")
    return synergies or ["Standard composition"]


def _counters(my_team, enemy_team) -> list[str]:
    my_names = {c.name for c in my_team}
    enemy_names = {c.name for c in enemy_team}
    counters = []
    if "Zed" in enemy_names and "Jinx" in my_names:
        counters.append("Zed can assassinate Jinx before she hypercarries")
    return counters or ["No significant counters identified"]