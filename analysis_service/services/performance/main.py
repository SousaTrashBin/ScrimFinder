"""
services/performance/main.py  —  Player Performance Analysis Service  (port 8003)
"""

from fastapi import FastAPI, Query, Path, HTTPException
from typing import Optional

from shared.schemas import ErrorResponse, Role, TipCategory, ImpactLevel
from services.performance.schemas import (
    PlayerPerformanceReport, ImprovementTips, ImprovementTip, MatchPerformanceReport, MatchResult
)
from services.performance import model as perf_model

app = FastAPI(
    title="Player Performance Analysis Service",
    description=(
        "Aggregated performance reports, improvement tips, and per-match breakdowns "
        "powered by a trained scoring model with tier-based percentile benchmarking.\n\n"
        "**Student:** Rodrigo Neto (fc59850)"
    ),
    version="1.0.0",
)


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok", "modelVersion": perf_model.current_version()}


@app.get(
    "/analysis/performance/{summoner_id}",
    response_model=PlayerPerformanceReport,
    summary="Get overall player performance analysis",
    description=(
        "Returns an aggregated performance report covering KDA, damage, vision score, "
        "gold per minute, and objective participation. Each metric is benchmarked "
        "against the EUW average for the player's tier."
    ),
    tags=["Player Performance"],
    responses={404: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def get_player_performance(
    summoner_id: str = Path(..., example="fc59850-RodrigoEUW"),
    champion: Optional[str] = Query(None),
    role: Optional[Role] = Query(None),
    lastN: int = Query(20, ge=1, le=100),
) -> PlayerPerformanceReport:
    try:
        # TODO: fetch real aggregated stats from the match history DB
        # stub stats:
        raw_stats = {
            "kda": 3.2,
            "avgDamageDealt": 22000.0,
            "avgDamageTaken": 18000.0,
            "avgVisionScore": 18.0,
            "avgGoldPerMinute": 380.0,
        }
        metrics = perf_model.compute_metrics(raw_stats, tier="GOLD")
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    return PlayerPerformanceReport(
        summonerId=summoner_id,
        matchesAnalyzed=lastN,
        champion=champion,
        role=role,
        winRate=54.0,
        kda=metrics.get("kda"),
        avgDamageDealt=metrics.get("avgDamageDealt"),
        avgDamageTaken=metrics.get("avgDamageTaken"),
        avgVisionScore=metrics.get("avgVisionScore"),
        avgGoldPerMinute=metrics.get("avgGoldPerMinute"),
        modelVersion=perf_model.current_version(),
    )


@app.get(
    "/analysis/performance/{summoner_id}/tips",
    response_model=ImprovementTips,
    summary="Get automated improvement tips for a player",
    description=(
        "Generates actionable improvement suggestions based on the player's "
        "recent performance metrics. Tips are ranked by estimated impact."
    ),
    tags=["Player Performance"],
    responses={404: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def get_player_tips(
    summoner_id: str = Path(..., example="fc59850-RodrigoEUW"),
    champion: Optional[str] = Query(None),
    lastN: int = Query(20, ge=1, le=100),
) -> ImprovementTips:
    try:
        raw_stats = {"kda": 3.2, "avgVisionScore": 14.0, "avgGoldPerMinute": 320.0}
        metrics = perf_model.compute_metrics(raw_stats, tier="GOLD")
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    tips = _generate_tips(metrics)

    return ImprovementTips(
        summonerId=summoner_id,
        matchesAnalyzed=lastN,
        tips=tips,
    )


@app.get(
    "/analysis/performance/{summoner_id}/match/{match_id}",
    response_model=MatchPerformanceReport,
    summary="Get per-match performance analysis for a player",
    description=(
        "Detailed post-game report comparing the player's stats against team "
        "and game averages. Includes highlights and lowlights."
    ),
    tags=["Player Performance"],
    responses={404: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def get_match_performance(
    summoner_id: str = Path(..., example="fc59850-RodrigoEUW"),
    match_id: str = Path(..., example="EUW1_7123456789"),
) -> MatchPerformanceReport:
    # TODO: fetch real match data from match history DB
    return MatchPerformanceReport(
        summonerId=summoner_id,
        matchId=match_id,
        champion="Jinx",
        role=Role.BOT,
        result=MatchResult.WIN,
        comparedToTeamAvg={"kda": 1.2, "damageDealt": 3400},
        comparedToGameAvg={"visionScore": -3.1},
        highlights=["Highest damage in game", "Most assists on team"],
        lowlights=["Below average vision score"],
        modelVersion=perf_model.current_version(),
    )


# ── Tip generation ────────────────────────────────────────────

def _generate_tips(metrics: dict) -> list[ImprovementTip]:
    tips = []
    if "avgVisionScore" in metrics and metrics["avgVisionScore"].percentile < 30:
        tips.append(ImprovementTip(
            category=TipCategory.VISION,
            tip="Your vision score is in the bottom 30% for your tier. Buy control wards every back.",
            impact=ImpactLevel.HIGH,
            relatedMetric="avgVisionScore",
        ))
    if "avgGoldPerMinute" in metrics and metrics["avgGoldPerMinute"].percentile < 40:
        tips.append(ImprovementTip(
            category=TipCategory.FARMING,
            tip="Your GPM is below average. Focus on not missing CS during laning phase.",
            impact=ImpactLevel.HIGH,
            relatedMetric="avgGoldPerMinute",
        ))
    if "kda" in metrics and metrics["kda"].percentile < 25:
        tips.append(ImprovementTip(
            category=TipCategory.POSITIONING,
            tip="High death count detected. Play safer near objectives and avoid overextending.",
            impact=ImpactLevel.MEDIUM,
            relatedMetric="kda",
        ))
    return tips or [ImprovementTip(
        category=TipCategory.GAME_SENSE,
        tip="Performance is on par with your tier. Focus on consistency and map awareness.",
        impact=ImpactLevel.LOW,
        relatedMetric=None,
    )]