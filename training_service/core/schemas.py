from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class Concern(str, Enum):
    DRAFT = "draft"
    BUILD = "build"
    PERFORMANCE = "performance"


class Algorithm(str, Enum):
    LOGISTIC = "logistic"
    GBM = "gbm"
    RANDOM_FOREST = "random_forest"
    LIGHTGBM = "lightgbm"
    AUTO = "auto"


class JobStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class ErrorResponse(BaseModel):
    code: int
    message: str
    details: Optional[str] = None


class PaginatedMeta(BaseModel):
    total: int
    limit: int
    offset: int


class GameIngest(BaseModel):
    id: Optional[str] = Field(
        None,
        description=(
            "Optional custom match id. Prefer Riot format REGION_MATCHID (example: VN_1417849076)."
        ),
    )
    data: dict = Field(..., description="Full match JSON")
    source: str = "manual"


class GameIngested(BaseModel):
    id: str
    source: str
    patch: Optional[str]
    match_type: Optional[str]
    ingested_at: str


class GameDetail(GameIngested):
    duration_sec: Optional[int]
    platform: Optional[str]
    raw_json: dict


class GameListResponse(BaseModel):
    games: list[GameIngested]
    meta: PaginatedMeta


class BatchIngestRequest(BaseModel):
    games: list[GameIngest]
    source: str = "manual"


class BatchIngestResponse(BaseModel):
    ingested: int
    skipped: int
    errors: list[dict]


class LeagueImportRequest(BaseModel):
    limit: int = Field(
        100,
        ge=1,
        le=10000,
        description="Maximum number of LEAGUE_DB matches to copy into ml-db.",
    )
    offset: int = Field(0, ge=0)
    match_type: Optional[str] = None


class LeagueImportResponse(BaseModel):
    imported: int
    skipped: int
    errors: list[dict]


class FeatureExtractRequest(BaseModel):
    game_id: Optional[str] = Field(
        None,
        description=(
            "Stored game id. Riot-style ids like 'VN_1417849076' are supported. "
            "You can derive this from LeagueOfGraphs match URLs, e.g. "
            "https://www.leagueofgraphs.com/match/vn/1417849076#participant8 -> VN_1417849076."
        ),
    )
    raw_data: Optional[dict] = None
    concerns: list[Concern] = Field(
        default=[Concern.DRAFT, Concern.BUILD, Concern.PERFORMANCE]
    )
    store: bool = True


class FeatureVector(BaseModel):
    game_id: str
    concern: str
    feature_vector: list[float]
    feature_names: list[str]
    schema_version: str
    extracted_at: str


class FeatureExtractResponse(BaseModel):
    game_id: str
    features: list[FeatureVector]
    stored: bool


class DatasetFilters(BaseModel):
    patch: Optional[str] = None
    patches: Optional[list[str]] = None
    match_type: Optional[str] = None
    min_duration_sec: Optional[int] = None
    max_duration_sec: Optional[int] = None
    source: Optional[str] = None
    date_from: Optional[str] = None
    date_to: Optional[str] = None


class DatasetCreateRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=128)
    concern: Concern
    description: str = ""
    filters: DatasetFilters = Field(default_factory=DatasetFilters)


class DatasetBuildRequest(BaseModel):
    name: str
    concern: Concern
    description: str = ""
    filters: DatasetFilters = Field(default_factory=DatasetFilters)


class DatasetMeta(BaseModel):
    id: str
    name: str
    description: str
    concern: str
    filters: dict
    game_count: int
    row_count: int
    status: str
    created_at: str
    built_at: Optional[str]
    file_path: Optional[str]


class DatasetListResponse(BaseModel):
    datasets: list[DatasetMeta]


class TrainingJobCreate(BaseModel):
    concern: Concern
    algorithm: Algorithm = Algorithm.AUTO
    sample: Optional[float] = Field(
        None, ge=0.01, le=1.0, description="Fraction of dataset to sample (0.01-1.0)"
    )
    limit: Optional[int] = Field(
        None, ge=1, le=100000, description="Max rows to train on"
    )
    match_type: Optional[str] = Field(
        None,
        description="Filter by match type (e.g., CLASSIC, ARAM). Leave empty for all.",
    )


class TrainingJobResponse(BaseModel):
    id: str
    concern: str
    algorithm: str
    status: str
    progress: int
    stage: str
    filters: dict
    metrics: Optional[dict] = None
    model_id: Optional[str] = None
    error: Optional[str] = None
    created_at: Optional[str] = None
    started_at: Optional[str] = None
    completed_at: Optional[str] = None


class TrainingJobListResponse(BaseModel):
    jobs: list[TrainingJobResponse]


class ModelMeta(BaseModel):
    id: str
    concern: str
    algorithm: str
    version: str
    metrics: dict
    hyperparams: dict
    is_active: bool
    created_at: Optional[str] = None
    activated_at: Optional[str] = None


class ModelListResponse(BaseModel):
    models: list[ModelMeta]
