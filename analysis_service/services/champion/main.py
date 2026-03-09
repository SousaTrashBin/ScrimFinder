"""
services/champion/main.py  —  Champion Analysis Service  (port 8004)

Endpoints
---------
GET /analysis/champion/{championName}/winrate
GET /analysis/champion/{championName}/winrate/by-rank
GET /analysis/champion/{championName}/builds
GET /analysis/champion/{championName}/positions
GET /analysis/champion/{championName}/matchups
GET /analysis/champion/{championName}/stats

GET /analysis/meta/top-champions
GET /analysis/meta/by-rank

GET /health
"""

from fastapi import FastAPI, Query, Path, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from typing import Optional

from shared.schemas import ErrorResponse
from services.champion.schemas import (
    Tier, Position, SortBy,
    ChampionWinRate, ChampionWinRateByRank, WinRateByTierEntry,
    ChampionBuildsResult, BuildEntry,
    ChampionPositionsResult, PositionEntry,
    ChampionMatchupsResult, MatchupEntry,
    ChampionStatsResult,
    TopChampionsResult, TopChampionEntry,
    MetaByRankResult, TierMetaEntry,
)
from services.champion import queries

app = FastAPI(
    title="Champion Analysis Service",
    description=(
        "Per-champion statistics and meta analysis derived from the EUW dataset.\n\n"
        "**Student:** Rodrigo Neto (fc59850)\n\n"
        "> **Note on tier filtering:** Tier parameters require Riot API enrichment. "
        "Without it, all data is returned regardless of tier."
    ),
    version="1.0.0",
)

app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

_ERR = {400: {"model": ErrorResponse}, 404: {"model": ErrorResponse}, 503: {"model": ErrorResponse}}


def _resolve_champion(name: str) -> int:
    """Look up champion_id or raise 404."""
    cid = queries.get_champion_id(name)
    if cid is None:
        raise HTTPException(status_code=404, detail=f"Champion '{name}' not found.")
    return cid


# ── Health ────────────────────────────────────────────────────

@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok", "service": "champion-analysis"}


# ══════════════════════════════════════════════════════════════
# CHAMPION ENDPOINTS
# ══════════════════════════════════════════════════════════════

@app.get(
    "/analysis/champion/{championName}/winrate",
    response_model=ChampionWinRate,
    summary="Get win rate for a champion",
    description=(
        "Returns overall win rate for a champion across the EUW dataset, "
        "with optional filters for position, rank tier and match type."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_winrate(
    championName: str = Path(..., example="Jinx"),
    position:     Optional[Position] = Query(None),
    tier:         Optional[Tier]     = Query(None, description="Requires Riot API enrichment."),
    matchType:    Optional[str]      = Query(None, example="RANKED"),
) -> ChampionWinRate:
    cid  = _resolve_champion(championName)
    data = queries.query_winrate(cid, position, matchType)
    total = data["total"]
    if total == 0:
        raise HTTPException(status_code=404, detail=f"No data found for champion '{championName}'.")
    return ChampionWinRate(
        champion=championName,
        position=position,
        tier=tier,
        winRate=round(data["wins"] / total * 100, 2),
        wins=int(data["wins"]),
        losses=int(data["losses"]),
        totalGames=total,
    )


@app.get(
    "/analysis/champion/{championName}/winrate/by-rank",
    response_model=ChampionWinRateByRank,
    summary="Get win rate breakdown across rank tiers",
    description=(
        "Returns the champion's win rate split per rank tier. "
        "Requires Riot API sync enrichment — returns empty breakdown without it."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_winrate_by_rank(
    championName: str = Path(..., example="Jinx"),
    position:     Optional[Position] = Query(None),
    matchType:    Optional[str]      = Query(None, example="RANKED"),
) -> ChampionWinRateByRank:
    _resolve_champion(championName)
    # TODO: implement once Riot API enrichment adds a tier column to player_stats
    # For now return empty breakdown with a clear message
    return ChampionWinRateByRank(
        champion=championName,
        position=position,
        breakdown=[],
    )


@app.get(
    "/analysis/champion/{championName}/builds",
    response_model=ChampionBuildsResult,
    summary="Get best item builds for a champion",
    description=(
        "Returns the top item builds ranked by win rate, computed from `player_items`. "
        "Each build must appear in at least `minSamples` games to be included."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_builds(
    championName: str = Path(..., example="Jinx"),
    position:     Optional[Position] = Query(None),
    tier:         Optional[Tier]     = Query(None),
    matchType:    Optional[str]      = Query(None, example="RANKED"),
    limit:        int                = Query(5,  ge=1, le=20),
    minSamples:   int                = Query(100, ge=10),
) -> ChampionBuildsResult:
    cid    = _resolve_champion(championName)
    rows   = queries.query_builds(cid, position, matchType, limit, minSamples)
    builds = [BuildEntry(**r) for r in rows]
    return ChampionBuildsResult(champion=championName, position=position, tier=tier, builds=builds)


@app.get(
    "/analysis/champion/{championName}/positions",
    response_model=ChampionPositionsResult,
    summary="Get best positions for a champion",
    description=(
        "Returns win rate and pick rate for each position this champion has been played in, "
        "sorted by win rate. Useful for spotting off-meta picks that actually work."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_positions(
    championName: str = Path(..., example="Jinx"),
    matchType:    Optional[str] = Query(None, example="RANKED"),
) -> ChampionPositionsResult:
    cid  = _resolve_champion(championName)
    rows = queries.query_positions(cid, matchType)
    if not rows:
        raise HTTPException(status_code=404, detail=f"No position data found for '{championName}'.")
    return ChampionPositionsResult(
        champion=championName,
        positions=[PositionEntry(**r) for r in rows],
    )


@app.get(
    "/analysis/champion/{championName}/matchups",
    response_model=ChampionMatchupsResult,
    summary="Get matchup win rates for a champion",
    description=(
        "Returns win rates against every opponent in the dataset, best matchups first. "
        "Scoped to the same lane by default — use `crossLane=true` for all positions."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_matchups(
    championName: str = Path(..., example="Jinx"),
    position:     Optional[Position] = Query(None),
    matchType:    Optional[str]      = Query(None, example="RANKED"),
    crossLane:    bool               = Query(False),
    limit:        int                = Query(20, ge=1, le=100),
    minSamples:   int                = Query(50, ge=10),
) -> ChampionMatchupsResult:
    cid  = _resolve_champion(championName)
    rows = queries.query_matchups(cid, position, matchType, crossLane, limit, minSamples)
    return ChampionMatchupsResult(
        champion=championName,
        position=position,
        matchups=[MatchupEntry(**r) for r in rows],
    )


@app.get(
    "/analysis/champion/{championName}/stats",
    response_model=ChampionStatsResult,
    summary="Get average performance stats for a champion",
    description=(
        "Returns aggregated KDA, damage, gold, CS, vision score and kill participation "
        "for a champion, with optional position and tier filters."
    ),
    tags=["Champion Analysis"],
    responses=_ERR,
)
def get_stats(
    championName: str = Path(..., example="Jinx"),
    position:     Optional[Position] = Query(None),
    tier:         Optional[Tier]     = Query(None),
    matchType:    Optional[str]      = Query(None, example="RANKED"),
) -> ChampionStatsResult:
    cid  = _resolve_champion(championName)
    data = queries.query_stats(cid, position, matchType)
    if data is None:
        raise HTTPException(status_code=404, detail=f"No stats found for '{championName}'.")
    return ChampionStatsResult(
        champion=championName,
        position=position,
        tier=tier,
        totalGames=data["total"],
        avgKills=data["avg_kills"],
        avgDeaths=data["avg_deaths"],
        avgAssists=data["avg_assists"],
        avgKda=data["avg_kda"],
        avgDamage=float(data["avg_damage"]),
        avgGold=float(data["avg_gold"]),
        avgCs=data["avg_cs"],
        avgVisionScore=data["avg_vision"],
        avgKp=data["avg_kp"],
    )


# ══════════════════════════════════════════════════════════════
# META ENDPOINTS
# ══════════════════════════════════════════════════════════════

@app.get(
    "/analysis/meta/top-champions",
    response_model=TopChampionsResult,
    summary="Get strongest champions overall or per position",
    description=(
        "Returns the top N champions ranked by the chosen metric. "
        "Includes sample size for statistical context."
    ),
    tags=["Meta Analysis"],
    responses=_ERR,
)
def get_top_champions(
    position:   Optional[Position] = Query(None),
    tier:       Optional[Tier]     = Query(None),
    matchType:  Optional[str]      = Query(None, example="RANKED"),
    limit:      int                = Query(10,  ge=1,  le=50),
    minSamples: int                = Query(200, ge=50),
    sortBy:     SortBy             = Query(SortBy.WIN_RATE),
) -> TopChampionsResult:
    rows = queries.query_top_champions(position, matchType, limit, minSamples, sortBy)
    return TopChampionsResult(
        position=position,
        tier=tier,
        sortedBy=sortBy,
        champions=[TopChampionEntry(**r) for r in rows],
    )


@app.get(
    "/analysis/meta/by-rank",
    response_model=MetaByRankResult,
    summary="Compare champion meta across all rank tiers",
    description=(
        "Returns top champions per tier so you can see how the meta shifts "
        "from Iron to Challenger. Requires Riot API enrichment for rank data."
    ),
    tags=["Meta Analysis"],
    responses=_ERR,
)
def get_meta_by_rank(
    position:  Optional[Position] = Query(None),
    matchType: Optional[str]      = Query(None, example="RANKED"),
    limit:     int                = Query(5, ge=1, le=20),
) -> MetaByRankResult:
    # TODO: implement once Riot API enrichment adds tier column
    return MetaByRankResult(position=position, tiers=[])