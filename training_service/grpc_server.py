"""
training_service/grpc_server.py
gRPC server for the Training Service.

Exposes:
  ForwardMatch   — receives a match_id, fetches raw JSON from detail_filling_service,
                   stores it, and extracts feature vectors
  GetActiveModel — returns metadata for the active model of a concern
  HealthCheck    — returns service health status

Run alongside the REST API:
  The grpc server runs on port 50051 in a separate thread.
  Started automatically when training_service/main.py starts.

Generated stubs (from training_service.proto):
  Run: python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/training_service.proto
  Then import: training_service_pb2, training_service_pb2_grpc
"""

import json
import os
import threading
from concurrent import futures

import grpc

from training_service.core import db

_server = None

# ── gRPC port ─────────────────────────────────────────────────
GRPC_PORT = int(os.environ.get("GRPC_PORT", 50051))

# ── Detail Filling Service URL ────────────────────────────────
DETAIL_FILLING_URL = os.environ.get(
    "DETAIL_FILLING_URL", "http://detail-filling-service:8080/api/v1/riot"
)


def _fetch_raw_match(match_id: str) -> dict:
    """
    Fetch raw Riot JSON from Bruno's detail_filling_service.
    GET /matches/{matchId}/raw
    """
    import urllib.request
    from urllib.parse import quote

    url = f"{DETAIL_FILLING_URL}/matches/{quote(match_id)}/raw"
    try:
        print(f"[gRPC] Fetching raw match from: {url}", flush=True)
        with urllib.request.urlopen(url, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        raise RuntimeError(
            f"Failed to fetch match {match_id} from detail_filling_service at {url}: {e}"
        )


# ── Servicer ──────────────────────────────────────────────────

# We import the base class inside a function or use a try-except to avoid
# startup failures if the .proto hasn't been compiled yet.
try:
    from training_service import training_service_pb2_grpc
    _BaseServicer = training_service_pb2_grpc.TrainingServiceServicer
except ImportError:
    _BaseServicer = object


class TrainingServiceServicer(_BaseServicer):
    """
    Implements the TrainingService gRPC interface.
    """

    def ForwardMatch(self, request, context):
        """
        Receive a match_id, fetch raw JSON, store, extract features.
        Called by match_history_service after a match is saved.
        """
        from training_service.training_service_pb2 import ForwardMatchResponse

        match_id = request.match_id
        source = request.source or "matchmaking"
        print(f"[gRPC] Received ForwardMatch for {match_id} (source={source})", flush=True)

        try:
            # 1. Fetch raw Riot JSON from detail_filling_service
            raw = _fetch_raw_match(match_id)
            print(f"[gRPC] Successfully fetched raw match {match_id}", flush=True)

            # 2. Validate
            from training_service.ingestion.feature_extractor import validate_riot_match

            valid, reason = validate_riot_match(raw)
            if not valid:
                print(f"[gRPC] Validation failed for {match_id}: {reason}", flush=True)
                return ForwardMatchResponse(
                    success=False,
                    message=f"Invalid match JSON: {reason}",
                    game_id=match_id,
                )

            # 3. Store raw game
            db.insert_game(match_id, raw, source=source)

            # 4. Extract features
            from training_service.ingestion.feature_extractor import extract

            features = extract(raw)

            draft_ok = build_ok = perf_ok = False

            # Store draft features
            if features.get("draft") and features["draft"].get("valid"):
                db.upsert_features(match_id, "draft", [features["draft"]], ["draft_raw"])
                draft_ok = True

            # Store build features (one per player)
            if features.get("build"):
                db.upsert_features(
                    match_id,
                    "build",
                    features["build"],
                    [f"player_{i}" for i in range(len(features["build"]))],
                )
                build_ok = True

            # Store performance features (one per player)
            if features.get("performance"):
                db.upsert_features(
                    match_id,
                    "performance",
                    features["performance"],
                    [f"player_{i}" for i in range(len(features["performance"]))],
                )
                perf_ok = True

            print(f"[gRPC] Successfully processed match {match_id}", flush=True)
            return ForwardMatchResponse(
                success=True,
                message=f"Match {match_id} ingested and features extracted.",
                game_id=match_id,
                draft_ok=draft_ok,
                build_ok=build_ok,
                perf_ok=perf_ok,
            )

        except Exception as e:
            print(f"[gRPC] Error processing match {match_id}: {e}", flush=True)
            import traceback
            traceback.print_exc()
            return ForwardMatchResponse(
                success=False,
                message=str(e),
                game_id=match_id,
            )

    def GetActiveModel(self, request, context):
        """
        Return metadata for the active model of a concern.
        Called by analysis_service instead of reading platform.db directly.
        """
        from training_service.training_service_pb2 import GetActiveModelResponse

        concern = request.concern
        try:
            row = db.get_active_model(concern)
            if row is None:
                return GetActiveModelResponse(
                    found=False,
                    concern=concern,
                    message=f"No active model for concern='{concern}'.",
                )
            return GetActiveModelResponse(
                found=True,
                model_id=row["id"],
                concern=row["concern"],
                algorithm=row["algorithm"],
                version=row["version"],
                file_path=row["file_path"],
                metrics_json=json.dumps(row.get("metrics", {})),
                activated_at=row.get("activated_at", ""),
            )
        except Exception:
            return GetActiveModelResponse(found=False, concern=concern)

    def HealthCheck(self, request, context):
        """Return training service health status."""
        from training_service.training_service_pb2 import HealthCheckResponse

        try:
            games = db.count_games()
            active = [m["concern"] for m in db.list_models(active_only=True)]
            return HealthCheckResponse(
                healthy=True,
                message="Training Service is healthy.",
                games_ingested=games,
                active_models=active,
            )
        except Exception as e:
            return HealthCheckResponse(healthy=False, message=str(e))


# ── Server management ──────────────────────────────────────────


def start_server(block: bool = False) -> grpc.Server:
    """
    Start the gRPC server.

    Args:
        block: If True, blocks the calling thread (use for standalone run).
               If False, starts in background thread (use when called from main.py).

    Returns:
        The running grpc.Server instance.
    """
    global _server

    if _server is not None:
        print("[gRPC] Server already running.")
        return _server

    try:
        from training_service import training_service_pb2_grpc
    except ImportError:
        print(
            "[gRPC] ERROR: training_service_pb2_grpc not found. "
            "Run: python -m grpc_tools.protoc -I proto "
            "--python_out=. --grpc_python_out=. proto/training_service.proto"
        )
        return None

    port = int(os.environ.get("GRPC_PORT", 50051))
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    training_service_pb2_grpc.add_TrainingServiceServicer_to_server(
        TrainingServiceServicer(), server
    )

    # Use 0.0.0.0 for better compatibility with Windows and standard networking
    # [::] can sometimes be restrictive on Windows if IPv6 isn't perfect
    server.add_insecure_port(f"0.0.0.0:{port}")
    server.start()
    _server = server

    print(f"[gRPC] Training Service listening on port {port}", flush=True)

    if block:
        server.wait_for_termination()

    return server


def stop_server(grace: int = 0):
    """Gracefully stop the gRPC server."""
    global _server
    if _server is not None:
        print("[gRPC] Stopping server...", flush=True)
        _server.stop(grace)
        _server = None


def start_background_server():
    """
    Start the gRPC server in a background thread.
    Used for FastAPI lifespan integration.
    """
    # Start in a daemon thread so it doesn't block process exit if stop() isn't called
    thread = threading.Thread(
        target=start_server,
        kwargs={"block": True},
        name="grpc-server-thread",
        daemon=True,
    )
    thread.start()


# ── Standalone entry point ────────────────────────────────────
if __name__ == "__main__":
    start_server(block=True)
