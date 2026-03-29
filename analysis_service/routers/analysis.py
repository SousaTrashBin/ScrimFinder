"""
analysis_service/routers/analysis.py
All analysis endpoints with real ML inference.

Draft:       GBM on champion multi-hot vectors → P(blue wins)
Build:       RandomForest on items+runes+position+champion → P(win)
Performance: GBM pipeline on player stats → P(win) + percentile benchmarking
Champion:    Direct SQL against league_data.db — no model needed
Player:      SQL aggregation + performance model percentiles
Game:        Per-player performance scoring using performance model
"""

import hashlib
import json
import pickle

import numpy as np
import Path
from fastapi import APIRouter, HTTPException

import analysis_service.grpc_client as grpc_client
from analysis_service.core.schemas import (
    AlternativeItem,
    BuildAnalysisRequest,
    BuildAnalysisResponse,
    ChampionAnalysisRequest,
    ChampionAnalysisResponse,
    ChampionStats,
    DraftAnalysisRequest,
    DraftAnalysisResponse,
    ErrorResponse,
    GameAnalysisRequest,
    GameAnalysisResponse,
    ImpactLevel,
    ImprovementTip,
    PerformanceMetric,
    PlayerAnalysisRequest,
    PlayerAnalysisResponse,
    PlayerOutcome,
    TipCategory,
)

router = APIRouter(prefix="/analysis", tags=["Analysis"])
_ERR = {503: {"model": ErrorResponse}, 404: {"model": ErrorResponse}}

# Position mapping — API role values → DB stored values
_ROLE_TO_DB = {
    "TOP": "TOP",
    "JUNGLE": "JUNGLE",
    "MID": "MIDDLE",
    "BOT": "BOTTOM",
    "SUPPORT": "UTILITY",
}

_DB_TO_ROLE = {v: k for k, v in _ROLE_TO_DB.items()}

PERF_METRICS = ["kills", "deaths", "assists", "gold", "cs", "dmg_champs", "vision", "kda", "kp"]


# ── Helpers ───────────────────────────────────────────────────


def _derive_id(data: dict) -> str:
    for k in ("matchId", "match_id", "gameId", "id"):
        if data.get(k):
            return str(data[k])
    return "game_" + hashlib.sha1(json.dumps(data, sort_keys=True).encode()).hexdigest()[:16]


def _model_version(concern: str) -> str | None:
    row = grpc_client.get_active_model(concern)
    return row["version"] if row else None


def _load_artifact(concern: str) -> tuple:
    """Load active model artifact. Raises 503 if unavailable."""
    row = grpc_client.get_active_model(concern)
    if row is None:
        raise HTTPException(
            status_code=503,
            detail=f"No active model for concern='{concern}'. "
            "Train one via the Training Service (POST /training/jobs).",
        )
    path = row.get("file_path")
    if not path or not Path.exists(path):
        raise HTTPException(status_code=503, detail=f"Model file not found at '{path}'. Re-train to regenerate.")
    with Path.open(path, "rb") as f:
        return pickle.load(f), row["version"]


def _percentile_score(value: float, percentile_data: dict, metric: str) -> float:
    """
    Compute approximate percentile of a value given the stored p10/p25/p50/p75/p90 buckets.
    Returns 0-100.
    """
    if metric not in percentile_data:
        return 50.0
    p = percentile_data[metric]
    if value <= p.get("p10", 0):
        return 10.0
    if value <= p.get("p25", 0):
        return 25.0
    if value <= p.get("p50", 0):
        return 50.0
    if value <= p.get("p75", 0):
        return 75.0
    if value <= p.get("p90", 0):
        return 90.0
    return 95.0


def _score_player(pipe, pos_le, champ_le, position_db: str, champion_id: int, stats: dict) -> float | None:
    """Run performance model on a single player's stats. Returns win probability."""
    try:
        pos_enc = pos_le.transform([position_db]).reshape(-1, 1)
        champ_enc = champ_le.transform([champion_id]).reshape(-1, 1)
        numeric = np.array(
            [
                [
                    stats.get("kills", 0),
                    stats.get("deaths", 0),
                    stats.get("assists", 0),
                    stats.get("gold", 0),
                    stats.get("cs", 0),
                    stats.get("dmg_champs", 0),
                    stats.get("vision", 0),
                    stats.get("kda", 0),
                    stats.get("kp", 0),
                ]
            ],
            dtype=np.float32,
        )
        X = np.hstack([pos_enc, champ_enc, numeric]).astype(np.float32)
        return round(float(pipe.predict_proba(X)[0][1]), 4)
    except Exception:
        return None


def _generate_tips(stats: dict, percentiles: dict, role: str) -> list[ImprovementTip]:
    """Generate improvement tips based on which metrics are below median."""
    tips = []
    role_p = percentiles.get(role, percentiles.get("MID", {}))

    checks = [
        (
            "vision",
            "vision",
            50,
            TipCategory.VISION,
            "Your vision score is below average. Place more wards and buy Control Wards.",
            ImpactLevel.HIGH,
        ),
        (
            "cs",
            "cs",
            40,
            TipCategory.FARMING,
            "Your CS is below average. Focus on last-hitting in the first 15 minutes.",
            ImpactLevel.HIGH,
        ),
        (
            "kda",
            "kda",
            40,
            TipCategory.POSITIONING,
            "Your KDA suggests you're dying too often. Play safer and trade more carefully.",
            ImpactLevel.MEDIUM,
        ),
        (
            "kp",
            "kp",
            35,
            TipCategory.OBJECTIVE_CONTROL,
            "Your kill participation is low. Rotate to help teammates more.",
            ImpactLevel.MEDIUM,
        ),
        (
            "dmg_champs",
            "dmg_champs",
            35,
            TipCategory.DAMAGE,
            "Your damage output is below average. Look for more aggressive trading patterns.",
            ImpactLevel.MEDIUM,
        ),
    ]

    for stat, metric, threshold_pct, category, tip_text, impact in checks:
        pct = _percentile_score(stats.get(stat, 0), role_p, metric)
        if pct < threshold_pct:
            tips.append(
                ImprovementTip(
                    category=category,
                    tip=tip_text,
                    impact=impact,
                    related_metric=metric,
                )
            )
        if len(tips) >= 3:
            break

    if not tips:
        tips.append(
            ImprovementTip(
                category=TipCategory.GAME_SENSE,
                tip="Your stats are above average. Focus on macro decisions and objective control.",
                impact=ImpactLevel.LOW,
            )
        )
    return tips


# ── /analysis/draft ───────────────────────────────────────────


@router.post(
    "/draft",
    response_model=DraftAnalysisResponse,
    summary="Win probability for a 5v5 draft",
    description=(
        "Returns ML-predicted win probabilities for both teams, plus "
        "synergy analysis, counter matchups, and win conditions. "
        "Requires an active draft model."
    ),
    responses=_ERR,
)
def analyze_draft(body: DraftAnalysisRequest) -> DraftAnalysisResponse:
    from analysis_service.champion.queries import get_champion_id

    mv = _model_version("draft")
    prob_blue = 0.5
    prob_red = 0.5

    try:
        artifact, mv = _load_artifact("draft")
        mlb = artifact["mlb"]
        clf = artifact["model"]

        def resolve(team) -> list[int]:
            return [cid for c in team.champions if (cid := get_champion_id(c.name)) is not None]

        blue_ids = resolve(body.team_blue)
        red_ids = resolve(body.team_red)

        if len(blue_ids) == 5 and len(red_ids) == 5:
            X = np.hstack([mlb.transform([blue_ids]), mlb.transform([red_ids])]).astype(np.float32)
            prob_red = round(float(clf.predict_proba(X)[0][1]), 4)
            prob_blue = round(1.0 - prob_red, 4)
    except HTTPException:
        pass

    return DraftAnalysisResponse(
        blue_win_probability=prob_blue,
        red_win_probability=prob_red,
        blue_synergies=_synergies(body.team_blue.champions),
        red_synergies=_synergies(body.team_red.champions),
        blue_counters=_counters(body.team_blue.champions, body.team_red.champions),
        red_counters=_counters(body.team_red.champions, body.team_blue.champions),
        win_conditions={
            "blue": _win_conditions(body.team_blue.champions),
            "red": _win_conditions(body.team_red.champions),
        },
        tips=_draft_tips(body.team_blue.champions, body.team_red.champions),
        model_version=mv,
    )


# ── /analysis/build ───────────────────────────────────────────


@router.post(
    "/build",
    response_model=BuildAnalysisResponse,
    summary="Build effectiveness score (0–100)",
    description=(
        "Scores an item build for a champion using the trained build model. "
        "Score is derived from predicted win probability. "
        "Optionally considers enemy composition for alternative suggestions."
    ),
    responses=_ERR,
)
def analyze_build(body: BuildAnalysisRequest) -> BuildAnalysisResponse:
    from analysis_service.champion.queries import get_champion_id, get_item_ids

    mv = _model_version("build")
    score = 50
    win_rate = None

    try:
        artifact, mv = _load_artifact("build")
        clf = artifact["model"]
        encoders = artifact["encoders"]
        item_mlb = encoders["item_mlb"]
        rune_mlb = encoders["rune_mlb"]
        pos_le = encoders["pos_le"]
        champ_le = encoders["champ_le"]

        cid = get_champion_id(body.champion)
        role_str = body.role.value if body.role else "BOT"
        pos_db = _ROLE_TO_DB.get(role_str, "BOTTOM")

        if cid is not None:
            # Resolve item names → IDs (as strings to match training)
            item_ids = [str(i) for i in get_item_ids(body.items)]
            item_enc = item_mlb.transform([item_ids])
            rune_enc = rune_mlb.transform([[]])  # no runes provided via API yet

            try:
                pos_enc = pos_le.transform([pos_db]).reshape(-1, 1)
                champ_enc = champ_le.transform([cid]).reshape(-1, 1)
                numeric = np.array([[0, 0, 0]], dtype=np.float32)
                X = np.hstack([item_enc, rune_enc, pos_enc, champ_enc, numeric]).astype(np.float32)
                win_prob = float(clf.predict_proba(X)[0][1])
                win_rate = round(win_prob, 4)
                score = int(win_prob * 100)
            except Exception:
                # Unknown champion or position — keep defaults
                pass

    except HTTPException:
        pass

    strengths, weaknesses = _eval_build(body.items, body.enemy_composition)
    return BuildAnalysisResponse(
        champion=body.champion,
        items=body.items,
        score=score,
        win_rate_with_build=win_rate,
        strengths=strengths,
        weaknesses=weaknesses,
        alternative_items=_suggest_alts(body.items, body.enemy_composition),
        model_version=mv,
    )


# ── /analysis/player ─────────────────────────────────────────


@router.post(
    "/player",
    response_model=PlayerAnalysisResponse,
    summary="Player performance analysis and improvement tips",
    description=(
        "Aggregates a player's recent match stats and benchmarks each metric "
        "against the EUW population for their role using stored percentile data. "
        "Returns prioritised improvement tips."
    ),
    responses=_ERR,
)
def analyze_player(body: PlayerAnalysisRequest) -> PlayerAnalysisResponse:
    from analysis_service.champion.queries import (
        query_player_stats,
    )

    mv = _model_version("performance")

    # ── Load performance model for percentile lookup ──────────
    percentiles = {}
    try:
        artifact, mv = _load_artifact("performance")
        percentiles = artifact["percentiles"]
    except HTTPException:
        pass

    # ── Query player stats from LEAGUE_DB ────────────────────
    try:
        stats_rows = query_player_stats(
            summoner_id=body.summoner_id,
            last_n=body.last_n_games,
            champion=body.champion,
            role=body.role.value if body.role else None,
            match_type=body.match_type,
        )
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"LEAGUE_DB unavailable: {e}") from e

    if not stats_rows:
        return PlayerAnalysisResponse(
            summoner_id=body.summoner_id,
            matches_analyzed=0,
            champion=body.champion,
            role=body.role.value if body.role else None,
            win_rate=0.0,
            kda=None,
            avg_damage=None,
            avg_vision=None,
            avg_gold_pm=None,
            obj_participation=None,
            tips=[
                ImprovementTip(
                    category=TipCategory.GAME_SENSE,
                    tip="No matches found for this player in the EUW dataset.",
                    impact=ImpactLevel.LOW,
                )
            ],
            model_version=mv,
        )

    # ── Aggregate stats ───────────────────────────────────────
    n = len(stats_rows)
    wins = sum(1 for r in stats_rows if r.get("win"))
    avgs = {m: round(sum(r.get(m, 0) for r in stats_rows) / n, 2) for m in PERF_METRICS}
    role = stats_rows[0].get("position", "MID")
    role = _DB_TO_ROLE.get(role, role)
    role_db = _ROLE_TO_DB.get(role, "MIDDLE")

    # Duration avg for gold-per-minute calculation
    avg_duration = sum(r.get("duration_sec", 1800) for r in stats_rows) / n
    avg_gpm = round((avgs["gold"] / max(avg_duration / 60, 1)), 1)

    # ── Percentile benchmarking ───────────────────────────────
    role_p = percentiles.get(role_db, percentiles.get("MIDDLE", {}))

    def make_metric(value: float, metric: str, tier_key: str) -> PerformanceMetric:
        pct = _percentile_score(value, role_p, tier_key)
        tier_avg = role_p.get(tier_key, {}).get("p50", 0.0) if role_p else 0.0
        return PerformanceMetric(value=value, tier_average=tier_avg, percentile=pct)

    # ── Tips ──────────────────────────────────────────────────
    tips = _generate_tips(avgs, percentiles, role_db)

    return PlayerAnalysisResponse(
        summoner_id=body.summoner_id,
        matches_analyzed=n,
        champion=body.champion,
        role=role,
        win_rate=round(wins / n, 4),
        kda=make_metric(avgs["kda"], "kda", "kda"),
        avg_damage=make_metric(avgs["dmg_champs"], "dmg_champs", "dmg_champs"),
        avg_vision=make_metric(avgs["vision"], "vision", "vision"),
        avg_gold_pm=PerformanceMetric(value=avg_gpm, tier_average=0.0, percentile=50.0),
        obj_participation=make_metric(avgs["kp"], "kp", "kp"),
        tips=tips,
        model_version=mv,
    )


# ── /analysis/game ────────────────────────────────────────────


@router.post(
    "/game",
    response_model=GameAnalysisResponse,
    summary="Post-game breakdown for all 10 players",
    description=(
        "Scores each player's performance using the performance model. "
        "Supply a `game_id` (stored game) or inline `raw_data`. "
        "Participants must have: puuid, championId, teamId, position, "
        "win, kills, deaths, assists, gold, cs, dmg_champs, vision, kda, kp."
    ),
    responses=_ERR,
)
def analyze_game(body: GameAnalysisRequest) -> GameAnalysisResponse:
    from analysis_service.champion.queries import get_champion_name_by_id

    # ── Resolve raw match data ────────────────────────────────
    if body.game_id:
        # Game_id provided but we don't store games in analysis service
        # Just use it as an identifier
        game_id = body.game_id
        raw = {}
    elif body.raw_data:
        game_id = _derive_id(body.raw_data)
        raw = body.raw_data
    else:
        raise HTTPException(status_code=422, detail="Provide game_id or raw_data.")

    mv = _model_version("performance")

    # ── Load performance model ────────────────────────────────
    pipe = None
    pos_le = None
    champ_le = None
    try:
        artifact, mv = _load_artifact("performance")
        pipe = artifact["pipeline"]
        pos_le = artifact["encoders"]["pos_le"]
        champ_le = artifact["encoders"]["champ_le"]
    except HTTPException:
        pass

    # ── Parse participants ────────────────────────────────────
    participants = raw.get("participants", [])
    players: list[PlayerOutcome] = []

    for p in participants:
        puuid = p.get("puuid", "unknown")
        champ_id = p.get("championId", 0)
        position = p.get("position", "MIDDLE")
        win = bool(p.get("win", False))
        kills = p.get("kills", 0)
        deaths = p.get("deaths", 0)
        assists = p.get("assists", 0)
        gold = p.get("gold", 0)
        cs = p.get("cs", 0)
        dmg = p.get("dmg_champs", 0)
        vision = p.get("vision", 0)
        kda = p.get("kda", round((kills + assists) / max(deaths, 1), 2))
        kp = p.get("kp", 0.0)

        champ_name = get_champion_name_by_id(champ_id) or str(champ_id)

        # Performance score from model
        perf_score = None
        if pipe and pos_le and champ_le:
            perf_score = _score_player(
                pipe,
                pos_le,
                champ_le,
                position,
                champ_id,
                {
                    "kills": kills,
                    "deaths": deaths,
                    "assists": assists,
                    "gold": gold,
                    "cs": cs,
                    "dmg_champs": dmg,
                    "vision": vision,
                    "kda": kda,
                    "kp": kp,
                },
            )

        highlights, lowlights = _highlight_player(kills, deaths, cs, vision, kda)

        players.append(
            PlayerOutcome(
                puuid=puuid,
                champion=champ_name,
                role=_DB_TO_ROLE.get(position, position),
                win=win,
                kda=kda,
                damage=dmg,
                gold=gold,
                cs=cs,
                vision=vision,
                performance_score=perf_score,
                highlights=highlights,
                lowlights=lowlights,
            )
        )

    # ── Determine winner ──────────────────────────────────────
    blue_wins = any(p.win for p in players if p.role in ("TOP", "JUNGLE", "MID", "BOT", "SUPPORT"))
    winner = "blue" if blue_wins else "red"

    return GameAnalysisResponse(
        game_id=game_id,
        patch=raw.get("patch") or raw.get("gameVersion"),
        duration_sec=raw.get("duration_sec") or raw.get("gameDuration"),
        winner=winner,
        players=players,
        team_synergies={
            "blue": _synergies(
                [p for p in players if raw.get("participants", [{}])[players.index(p)].get("teamId") == 100]
            ),
            "red": [],
        },
        key_moments=_key_moments(players),
        model_version=mv,
    )


# ── /analysis/champion ────────────────────────────────────────


@router.post(
    "/champion",
    response_model=ChampionAnalysisResponse,
    summary="Champion stats from EUW dataset",
    description=(
        "Returns win rate, pick rate, average stats, and counter data "
        "for a champion. Queries the EUW dataset directly — no model required."
    ),
    responses=_ERR,
)
def analyze_champion(body: ChampionAnalysisRequest) -> ChampionAnalysisResponse:
    from analysis_service.champion.queries import (
        get_champion_id,
        query_counters,
        query_stats,
        query_top_items,
        query_winrate,
    )

    try:
        cid = get_champion_id(body.champion)
        if cid is None:
            raise HTTPException(status_code=404, detail=f"Champion '{body.champion}' not found in EUW dataset.")

        pos_db = _ROLE_TO_DB.get(body.position, body.position) if body.position else None

        wr = query_winrate(cid, position=pos_db, match_type=body.match_type)
        stats = query_stats(cid, position=pos_db, match_type=body.match_type)
        items = query_top_items(cid, position=pos_db, match_type=body.match_type)
        counters, countered_by = query_counters(cid, position=pos_db)

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"LEAGUE_DB unavailable: {e}") from e

    total = wr.get("total", 0)
    win_rate = round(wr.get("wins", 0) / max(total, 1) * 100, 2)
    tier = (
        "S" if win_rate >= 53 else "A" if win_rate >= 51 else "B" if win_rate >= 49 else "C" if win_rate >= 47 else "D"
    )

    return ChampionAnalysisResponse(
        champion=body.champion,
        tier=tier,
        stats=ChampionStats(
            champion=body.champion,
            position=body.position,
            win_rate=win_rate,
            pick_rate=0.0,  # TODO: needs total match count from DB
            total_games=total,
            avg_kda=stats.get("avgKda", 0.0),
            avg_damage=stats.get("avgDamage", 0.0),
            avg_gold=stats.get("avgGold", 0.0),
            avg_cs=stats.get("avgCs", 0.0),
            avg_vision=stats.get("avgVisionScore", 0.0),
            best_items=items,
            best_runes=[],  # TODO: query_top_runes
            counters=counters,
            countered_by=countered_by,
        ),
        model_version=_model_version("performance"),
    )


# ═════════════════════════════════════════════════════════════
# Rule-based helpers
# ═════════════════════════════════════════════════════════════


def _synergies(champs) -> list[str]:
    if not champs:
        return []
    names = {getattr(c, "name", getattr(c, "champion", "")) for c in champs}
    result = []
    for pair, label in [
        ({"Amumu", "Orianna"}, "Amumu + Orianna AoE wombo-combo"),
        ({"Jinx", "Lulu"}, "Jinx + Lulu hypercarry protection"),
        ({"Yasuo", "Malphite"}, "Yasuo + Malphite tower-dive combo"),
        ({"Sett", "Orianna"}, "Sett + Orianna ball delivery wombo"),
    ]:
        if pair.issubset(names):
            result.append(label)
    return result or ["Standard composition"]


def _counters(my_team, enemy_team) -> list[str]:
    mine = {getattr(c, "name", "") for c in my_team}
    theirs = {getattr(c, "name", "") for c in enemy_team}
    result = []
    for attacker, target, label in [
        ("Zed", "Jinx", "Zed can assassinate Jinx before she hypercarries"),
        ("Malphite", "Yasuo", "Malphite's ult knocks up Yasuo reliably"),
        ("Fiora", "Malphite", "Fiora's true damage bypasses Malphite's armour"),
        ("Fizz", "Syndra", "Fizz can dodge Syndra's stun with E"),
    ]:
        if attacker in theirs and target in mine:
            result.append(label)
    return result or ["No significant counter-matchups identified"]


def _win_conditions(champs) -> list[str]:
    names = {getattr(c, "name", "") for c in champs}
    conds = []
    if {"Amumu", "Orianna", "Malphite"} & names:
        conds.append("Force 5v5 teamfights — AoE ultimates are devastating")
    if {"Fiora", "Tryndamere", "Jax"} & names:
        conds.append("Split push and create pressure on multiple lanes")
    if {"Jinx", "Caitlyn", "Aphelios"} & names:
        conds.append("Protect the ADC and win extended teamfights")
    if {"Zed", "Talon", "Fizz"} & names:
        conds.append("Pick off isolated targets before grouping")
    return conds or ["Control vision, secure objectives, play around power spikes"]


def _draft_tips(blue, red) -> list[str]:
    tips = []
    blue_names = {getattr(c, "name", "") for c in blue}
    red_names = {getattr(c, "name", "") for c in red}
    if {"Amumu", "Orianna"} <= blue_names:
        tips.append("Blue: Wait for Malphite/Amumu to engage before Orianna ults.")
    if "Jinx" in blue_names:
        tips.append("Blue: Keep Jinx safe — she snowballs hard if she gets a reset.")
    if "Zed" in red_names and "Jinx" in blue_names:
        tips.append("Blue: Build Stopwatch on Jinx to survive Zed's assassination window.")
    tips.append("Ward river bushes before Dragon spawns.")
    return tips


def _eval_build(items: list[str], enemies: list[str] | None) -> tuple[list[str], list[str]]:
    s, w = [], []
    item_set = set(items)
    if "Kraken Slayer" in item_set:
        s.append("Strong tank-shredding capability")
    if "Infinity Edge" in item_set:
        s.append("High critical strike damage ceiling")
    if "Runaan's Hurricane" in item_set:
        s.append("Multi-target AoE for teamfights")
    if "Rabadon's Deathcap" in item_set:
        s.append("Massive AP amplification")
    if "Zhonya's Hourglass" in item_set:
        s.append("Invulnerability to survive burst")
    if enemies and "Malphite" in enemies and "Kraken Slayer" not in item_set:
        w.append("No tank-shred vs heavy-armour enemy (consider Kraken Slayer)")
    if enemies and any(e in enemies for e in ["Zed", "Talon", "Fizz"]) and "Zhonya's Hourglass" not in item_set:
        w.append("No survivability vs assassins (consider Zhonya's Hourglass)")
    return s or ["Balanced general-purpose build"], w or ["No notable weaknesses identified"]


def _suggest_alts(items: list[str], enemies: list[str] | None) -> list[AlternativeItem]:
    alts = []
    if enemies and "Malphite" in enemies and "Runaan's Hurricane" in items:
        alts.append(
            AlternativeItem(
                replaces="Runaan's Hurricane",
                suggested_item="Kraken Slayer",
                reason="Kraken Slayer's passive shreds Malphite's armour more effectively.",
            )
        )
    if enemies and any(e in enemies for e in ["Zed", "Talon"]) and "Banshee's Veil" not in items:
        alts.append(
            AlternativeItem(
                replaces=items[-1] if items else "Last item",
                suggested_item="Zhonya's Hourglass",
                reason="Zhonya's active lets you survive the assassination window.",
            )
        )
    return alts


def _highlight_player(kills: int, deaths: int, cs: int, vision: int, kda: float) -> tuple[list[str], list[str]]:
    highlights, lowlights = [], []
    if kills >= 10:
        highlights.append(f"Outstanding {kills} kills")
    if deaths == 0:
        highlights.append("Perfect deathless game")
    if cs >= 250:
        highlights.append(f"Excellent {cs} CS")
    if vision >= 40:
        highlights.append(f"Strong vision control ({vision})")
    if kda >= 5:
        highlights.append(f"Exceptional KDA of {kda}")
    if deaths >= 10:
        lowlights.append(f"Died {deaths} times — positioning needs work")
    if cs < 100:
        lowlights.append(f"Only {cs} CS — focus on farm")
    if vision < 10:
        lowlights.append("Low vision score — buy more wards")
    return highlights, lowlights


def _key_moments(players: list[PlayerOutcome]) -> list[str]:
    moments = []
    for p in players:
        if p.performance_score and p.performance_score >= 0.8:
            moments.append(f"{p.champion} had an outstanding performance ({p.performance_score:.0%} win probability)")
        if p.kda >= 8:
            moments.append(f"{p.champion} dominated with {p.kda} KDA")
        if p.vision >= 40:
            moments.append(f"{p.champion} had excellent vision control ({p.vision})")
    return moments or ["No standout moments — add full participant stats for detailed analysis"]
