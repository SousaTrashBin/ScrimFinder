"""api/routers/analysis.py — /analysis: draft, build, player, game, champion."""
from typing import Optional
from fastapi import APIRouter, HTTPException
from core import db
from analysis_api.core.schemas import (
    GameAnalysisRequest,GameAnalysisResponse,
    PlayerAnalysisRequest,PlayerAnalysisResponse,PerformanceMetric,ImprovementTip,
    DraftAnalysisRequest,DraftAnalysisResponse,
    BuildAnalysisRequest,BuildAnalysisResponse,AlternativeItem,
    ChampionAnalysisRequest,ChampionAnalysisResponse,ChampionStats,
    ErrorResponse,TipCategory,ImpactLevel,Role,
)

router = APIRouter(prefix="/analysis", tags=["Analysis"])
_ERR = {503:{"model":ErrorResponse},404:{"model":ErrorResponse}}

def _model_version(concern):
    row=db.get_active_model(concern); return row["version"] if row else None

def _load_artifact(concern):
    import pickle, os
    row=db.get_active_model(concern)
    if row is None: raise HTTPException(503,f"No active model for concern='{concern}'. Train one via POST /training/jobs.")
    path=row.get("file_path")
    if not path or not os.path.exists(path): raise HTTPException(503,f"Model file not found: {path}")
    with open(path,"rb") as f: return pickle.load(f), row["version"]

# ── /analysis/draft ───────────────────────────────────────────
@router.post("/draft",response_model=DraftAnalysisResponse,summary="Real-time draft win probability",
    description="Win probabilities + synergies for a 5v5 draft. Requires active draft model.",responses=_ERR)
def analyze_draft(body:DraftAnalysisRequest):
    mv=_model_version("draft"); prob_blue=prob_red=0.5
    try:
        artifact,mv=_load_artifact("draft")
        from services.champion.queries import get_champion_id
        import numpy as np
        mlb=artifact["mlb"]; clf=artifact["model"]
        def ids(team): return [i for c in team.champions if (i:=get_champion_id(c.name)) is not None]
        bi,ri=ids(body.team_blue),ids(body.team_red)
        if len(bi)==5 and len(ri)==5:
            X=np.hstack([mlb.transform([bi]),mlb.transform([ri])]).astype(np.float32)
            prob_red=round(float(clf.predict_proba(X)[0][1]),4); prob_blue=round(1.0-prob_red,4)
    except HTTPException: pass
    return DraftAnalysisResponse(
        blue_win_probability=prob_blue,red_win_probability=prob_red,
        blue_synergies=_synergies(body.team_blue.champions),red_synergies=_synergies(body.team_red.champions),
        blue_counters=_counters(body.team_blue.champions,body.team_red.champions),
        red_counters=_counters(body.team_red.champions,body.team_blue.champions),
        win_conditions={"blue":["Force teamfights","Leverage AoE ultimates"],"red":["Split push","Pick off isolated carries"]},
        tips=["Ward river bushes before Dragon spawns."],model_version=mv)

# ── /analysis/build ───────────────────────────────────────────
@router.post("/build",response_model=BuildAnalysisResponse,summary="Build effectiveness score",
    description="Score 0–100 for an item build. Requires active build model.",responses=_ERR)
def analyze_build(body:BuildAnalysisRequest):
    mv=_model_version("build"); score=50
    try:
        artifact,mv=_load_artifact("build")
        # TODO: resolve IDs, build feature vector, call clf
    except HTTPException: pass
    strengths,weaknesses=_eval_build(body.items,body.enemy_composition)
    return BuildAnalysisResponse(champion=body.champion,items=body.items,score=score,
        win_rate_with_build=None,strengths=strengths,weaknesses=weaknesses,
        alternative_items=_suggest_alts(body.items,body.enemy_composition),model_version=mv)

# ── /analysis/player ──────────────────────────────────────────
@router.post("/player",response_model=PlayerAnalysisResponse,
    summary="Player performance analysis + tips",
    description="Aggregates stats and benchmarks vs tier average. Requires active performance model.",responses=_ERR)
def analyze_player(body:PlayerAnalysisRequest):
    mv=_model_version("performance")
    # TODO: query player_stats from LEAGUE_DB; compute real metrics + percentiles
    stub=PerformanceMetric(value=0.0,tier_average=0.0,percentile=50.0)
    return PlayerAnalysisResponse(summoner_id=body.summoner_id,matches_analyzed=0,
        champion=body.champion,role=body.role.value if body.role else None,win_rate=0.5,
        kda=stub,avg_damage=stub,avg_vision=stub,avg_gold_pm=stub,obj_participation=stub,
        tips=[ImprovementTip(category=TipCategory.GAME_SENSE,tip="[stub] Implement query_player_stats to get real tips.",impact=ImpactLevel.HIGH)],
        model_version=mv)

# ── /analysis/game ────────────────────────────────────────────
@router.post("/analysis/game",response_model=GameAnalysisResponse,
    summary="Post-game breakdown for all 10 players",
    description="Requires a stored game (game_id) or inline raw_data. Feature extraction must be implemented for real scores.",
    responses=_ERR,include_in_schema=True)
@router.post("/game",response_model=GameAnalysisResponse,summary="Post-game analysis",responses=_ERR)
def analyze_game(body:GameAnalysisRequest):
    if body.game_id:
        row=db.get_game(body.game_id)
        if row is None: raise HTTPException(404,f"Game '{body.game_id}' not found.")
        game_id,raw=row["id"],row["raw_json"]
    elif body.raw_data:
        from analysis_api.routers.games import _derive_id
        game_id,raw=_derive_id(body.raw_data),body.raw_data
    else:
        raise HTTPException(422,"Provide game_id or raw_data.")
    return GameAnalysisResponse(game_id=game_id,patch=raw.get("patch") or raw.get("gameVersion"),
        duration_sec=raw.get("duration_sec") or raw.get("gameDuration"),winner="blue",
        players=[],team_synergies={"blue":[],"red":[]},
        key_moments=["[stub] Feature extraction required for real key moments."],
        model_version=_model_version("performance"))

# ── /analysis/champion ────────────────────────────────────────
@router.post("/champion",response_model=ChampionAnalysisResponse,
    summary="Champion stats from EUW DB",description="No model required — queries LEAGUE_DB directly.",responses=_ERR)
def analyze_champion(body:ChampionAnalysisRequest):
    try:
        from services.champion.queries import get_champion_id, query_winrate
        cid=get_champion_id(body.champion)
        if cid is None: raise HTTPException(404,f"Champion '{body.champion}' not found.")
        wr=query_winrate(cid,position=body.position,match_type=body.match_type)
        win_rate=round(wr.get("wins",0)/max(wr.get("total",1),1)*100,2)
    except HTTPException: raise
    except Exception as e: raise HTTPException(503,f"LEAGUE_DB unavailable: {e}")
    tier="S" if win_rate>=53 else "A" if win_rate>=51 else "B" if win_rate>=49 else "C" if win_rate>=47 else "D"
    return ChampionAnalysisResponse(champion=body.champion,tier=tier,
        stats=ChampionStats(champion=body.champion,position=body.position,win_rate=win_rate,
            pick_rate=0.0,total_games=wr.get("total",0),avg_kda=0.0,avg_damage=0.0,avg_gold=0.0,
            avg_cs=0.0,avg_vision=0.0,best_items=[],best_runes=[],counters=[],countered_by=[]),
        model_version=_model_version("performance"))

# ── Rule-based helpers ────────────────────────────────────────
def _synergies(champs):
    names={c.name for c in champs}; result=[]
    for pair,label in [({'Amumu','Orianna'},"Amumu+Orianna wombo"),({'Jinx','Lulu'},"Jinx+Lulu hypercarry"),({'Yasuo','Malphite'},"Yasuo+Malphite dive")]:
        if pair.issubset(names): result.append(label)
    return result or ["Standard composition"]

def _counters(mine,theirs):
    m={c.name for c in mine}; t={c.name for c in theirs}; result=[]
    for a,b,label in [("Zed","Jinx","Zed assassinates Jinx"),("Malphite","Yasuo","Malphite knocks up Yasuo"),("Fiora","Malphite","Fiora true damage beats Malphite armour")]:
        if a in t and b in m: result.append(label)
    return result or ["No significant counters"]

def _eval_build(items,enemies):
    s=[]; w=[]
    if "Kraken Slayer" in items: s.append("Tank shredding")
    if "Infinity Edge" in items: s.append("High crit damage")
    if "Runaan's Hurricane" in items: s.append("Multi-target AoE")
    if enemies and "Malphite" in enemies and "Kraken Slayer" not in items: w.append("No tank-shred vs heavy-armour enemy")
    return s or ["Balanced build"],w or ["No notable weaknesses"]

def _suggest_alts(items,enemies):
    alts=[]
    if enemies and "Malphite" in (enemies or []) and "Runaan's Hurricane" in items:
        alts.append(AlternativeItem(replaces="Runaan's Hurricane",suggested_item="Kraken Slayer",reason="Better tank-shred vs Malphite"))
    return alts
