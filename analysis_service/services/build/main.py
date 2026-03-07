"""
services/build/main.py  —  Build Analysis Service  (port 8002)
"""

from fastapi import FastAPI, Query, HTTPException
from typing import Optional

from shared.schemas import ErrorResponse, Role
from services.build.schemas import (
    BuildAnalysisRequest, BuildAnalysisResult,
    BuildRecommendation, AlternativeItem,
)
from services.build import model as build_model

app = FastAPI(
    title="Build Analysis Service",
    description=(
        "Evaluates and recommends item builds using a trained scoring model.\n\n"
        "**Student:** Rodrigo Neto (fc59850)"
    ),
    version="1.0.0",
)


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok", "modelVersion": build_model.current_version()}


@app.post(
    "/analysis/build",
    response_model=BuildAnalysisResult,
    summary="Analyse an item build for a champion",
    description=(
        "Evaluates a given item build considering the enemy team composition. "
        "Returns an effectiveness score (0–100), strengths, weaknesses, and "
        "alternative item suggestions from EUW high-elo data."
    ),
    tags=["Build Analysis"],
    responses={400: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def analyze_build(body: BuildAnalysisRequest) -> BuildAnalysisResult:
    try:
        score, win_rate = build_model.score_build(
            champion=body.champion,
            items=body.items,
            enemy_comp=body.enemyComposition,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    strengths, weaknesses = _evaluate_build(body.items, body.enemyComposition)

    return BuildAnalysisResult(
        champion=body.champion,
        items=body.items,
        score=score,
        strengths=strengths,
        weaknesses=weaknesses,
        alternativeItems=_suggest_alternatives(body.items, body.enemyComposition),
        winRateWithBuild=win_rate,
        modelVersion=build_model.current_version(),
    )


@app.get(
    "/analysis/build/recommend",
    response_model=list[BuildRecommendation],
    summary="Get recommended builds for a champion",
    description=(
        "Returns the top item builds for a champion in a given role, ranked by "
        "win rate and popularity in EUW high-elo matches."
    ),
    tags=["Build Analysis"],
    responses={400: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
def get_recommended_builds(
    champion: str = Query(..., example="Jinx"),
    role: Optional[Role] = Query(None),
    limit: int = Query(3, ge=1, le=10),
) -> list[BuildRecommendation]:
    # TODO: query pre-computed top-build table derived from EUW dataset
    return [
        BuildRecommendation(
            rank=1,
            items=["Kraken Slayer", "Runaan's Hurricane", "Infinity Edge"],
            winRate=55.2,
            pickRate=38.1,
            sampleSize=12400,
            notes="Standard hypercarry — strongest in even or winning games.",
        )
    ][:limit]


# ── Helpers (stub rule-based enrichment) ─────────────────────

def _evaluate_build(items: list[str], enemy_comp: list[str] | None):
    strengths, weaknesses = [], []
    if "Infinity Edge" in items:
        strengths.append("High crit damage output")
    if "Kraken Slayer" in items:
        strengths.append("True damage shreds tanks")
    if enemy_comp and len([e for e in enemy_comp if e in {"Malphite", "Amumu", "Leona"}]) >= 2:
        weaknesses.append("Vulnerable to heavy CC compositions")
    if len(items) < 3:
        weaknesses.append("Incomplete build reduces effectiveness")
    return strengths or ["Solid all-round build"], weaknesses or ["No major weaknesses identified"]


def _suggest_alternatives(items: list[str], enemy_comp: list[str] | None) -> list[AlternativeItem]:
    suggestions = []
    if enemy_comp and "Malphite" in enemy_comp and "Runaan's Hurricane" in items:
        suggestions.append(AlternativeItem(
            replaces="Runaan's Hurricane",
            suggestedItem="Wit's End",
            reason="Provides MR and on-hit damage vs AP-heavy frontline",
        ))
    return suggestions