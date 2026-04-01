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
    "DETAIL_FILLING_URL", "http://detail_filling_service:8080/api/v1/riot"
)


def _fetch_raw_match(match_id: str) -> dict:
    """
    Fetch raw Riot JSON from Bruno's detail_filling_service.
    GET /matches/{matchId}/raw
    """
    import urllib.request

    url = f"{DETAIL_FILLING_URL}/matches/{match_id}/raw"
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        raise RuntimeError(
            f"Failed to fetch match {match_id} from detail_filling_service: {e}"
        )


# ── Servicer ──────────────────────────────────────────────────


class TrainingServiceServicer:
    """
    Implements the TrainingService gRPC interface.
    Uses the generated pb2 types but works without them
    for IDE compatibility — the actual pb2 import happens
    at server start time.
    """

    def ForwardMatch(self, request, context):
        """
        Receive a match_id, fetch raw JSON, store, extract features.
        Called by match_history_service after a match is saved.
        """
        from training_service.training_service_pb2 import ForwardMatchResponse

        match_id = request.match_id
        source = request.source or "matchmaking"

        try:
            # 1. Fetch raw Riot JSON from detail_filling_service
            raw = _fetch_raw_match(match_id)

            # 2. Validate
            from training_service.ingestion.feature_extractor import validate_riot_match

            valid, reason = validate_riot_match(raw)
            if not valid:
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
                draft_vec = json.dumps(features["draft"])
                db.upsert_features(match_id, "draft", [draft_vec], ["draft_raw"])
                draft_ok = True

            # Store build features (one per player)
            if features.get("build"):
                build_vecs = [json.dumps(b) for b in features["build"]]
                db.upsert_features(
                    match_id,
                    "build",
                    build_vecs,
                    [f"player_{i}" for i in range(len(build_vecs))],
                )
                build_ok = True

            # Store performance features (one per player)
            if features.get("performance"):
                perf_vecs = [json.dumps(p) for p in features["performance"]]
                db.upsert_features(
                    match_id,
                    "performance",
                    perf_vecs,
                    [f"player_{i}" for i in range(len(perf_vecs))],
                )
                perf_ok = True

            return ForwardMatchResponse(
                success=True,
                message=f"Match {match_id} ingested and features extracted.",
                game_id=match_id,
                draft_ok=draft_ok,
                build_ok=build_ok,
                perf_ok=perf_ok,
            )

        except Exception as e:
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


# ── Server startup ────────────────────────────────────────────


def serve(block: bool = True) -> grpc.Server:
    """
    Start the gRPC server.

    Args:
        block: If True, blocks the calling thread (use for standalone run).
               If False, starts in background thread (use when called from main.py).

    Returns:
        The running grpc.Server instance.
    """
    try:
        from training_service import training_service_pb2_grpc
    except ImportError:
        print(
            "[gRPC] WARNING: training_service_pb2_grpc not found. "
            "Run: python -m grpc_tools.protoc -I proto "
            "--python_out=. --grpc_python_out=. proto/training_service.proto"
        )
        return None

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    training_service_pb2_grpc.add_TrainingServiceServicer_to_server(
        TrainingServiceServicer(), server
    )
    server.add_insecure_port(f"[::]:{GRPC_PORT}")
    server.start()
    print(f"[gRPC] Training Service listening on port {GRPC_PORT}")

    if block:
        server.wait_for_termination()
    return server


def start_background_server():
    """
    Start the gRPC server in a background thread.
    Keeps the thread alive via wait_for_termination() so the server continues listening.
    """
    global _server

    if _server is not None:
        return  # already running

    def _run_server():
        try:
            import os
            from concurrent import futures

            import grpc

            from training_service import training_service_pb2_grpc

            port = int(os.environ.get("GRPC_PORT", 50051))

            _server_local = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

            training_service_pb2_grpc.add_TrainingServiceServicer_to_server(
                TrainingServiceServicer(), _server_local
            )

            # Use 0.0.0.0 instead of [::] for Docker compatibility
            _server_local.add_insecure_port(f"0.0.0.0:{port}")
            _server_local.start()

            print(f"[gRPC] server running on port {port}", flush=True)

            # BLOCK HERE - keeps thread alive and server listening
            _server_local.wait_for_termination()

        except Exception as e:
            print(f"[gRPC] ERROR: {e}", flush=True)

    # Start in a non-daemon thread so it survives
    thread = threading.Thread(target=_run_server, name="grpc-server", daemon=False)
    thread.start()


# ── Standalone entry point ────────────────────────────────────
if __name__ == "__main__":
    serve(block=True)
