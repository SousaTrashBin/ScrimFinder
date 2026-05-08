"""
analysis_service/grpc_client.py
gRPC client for the Analysis Service.

Calls the Training Service to get active model metadata,
replacing the direct platform.db reads once gRPC is fully wired.

Falls back to direct DB reads if Training Service is unreachable,
so the Analysis Service degrades gracefully if Training is down.
"""

import json
import os
import threading

# ── Training Service gRPC address ────────────────────────────
TRAINING_GRPC_URL = os.environ.get("TRAINING_GRPC_URL", "localhost:50051")

_channel = None
_lock = threading.Lock()


def _get_channel():
    """Lazy-initialise the gRPC channel."""
    global _channel
    with _lock:
        if _channel is not None:
            return _channel
        try:
            import grpc

            _channel = grpc.insecure_channel(TRAINING_GRPC_URL)
            return _channel
        except ImportError:
            return None
        except Exception:
            return None


def _call_unary(method_paths: list[str], request, response_parser, timeout: int = 5):
    """Call unary gRPC endpoint trying multiple method paths for compatibility."""
    channel = _get_channel()
    if channel is None:
        return None
    for method in method_paths:
        try:
            unary = channel.unary_unary(
                method,
                request_serializer=lambda m: m.SerializeToString(),
                response_deserializer=response_parser,
            )
            return unary(request, timeout=timeout)
        except Exception:
            continue
    return None


def get_active_model_grpc(concern: str) -> dict | None:
    """
    Get active model metadata from Training Service via gRPC.
    Returns None if Training Service is unreachable.

    Returns dict with keys:
      found, model_id, concern, algorithm, version,
      file_path, metrics_json, activated_at
    """
    try:
        from analysis_service import training_service_pb2

        request = training_service_pb2.GetActiveModelRequest(concern=concern)
        response = _call_unary(
            [
                "/fc.ul.scrimfinder.grpc.TrainingService/GetActiveModel",
                "/scrimfinder.TrainingService/GetActiveModel",
            ],
            request,
            training_service_pb2.GetActiveModelResponse.FromString,
        )
        if response is None:
            return None

        if not response.found:
            return None

        return {
            "id": response.model_id,
            "concern": response.concern,
            "algorithm": response.algorithm,
            "version": response.version,
            "file_path": response.file_path,
            "metrics": json.loads(response.metrics_json) if response.metrics_json else {},
            "activated_at": response.activated_at,
            "is_active": True,
        }
    except Exception:
        return None


def health_check_grpc() -> dict:
    """
    Check if the Training Service is healthy via gRPC.
    Returns dict with healthy, message, games_ingested, active_models.
    """
    try:
        from analysis_service import training_service_pb2

        response = _call_unary(
            [
                "/fc.ul.scrimfinder.grpc.TrainingService/HealthCheck",
                "/scrimfinder.TrainingService/HealthCheck",
            ],
            training_service_pb2.HealthCheckRequest(),
            training_service_pb2.HealthCheckResponse.FromString,
        )
        if response is None:
            return {"healthy": False, "message": "gRPC unavailable"}
        return {
            "healthy": response.healthy,
            "message": response.message,
            "games_ingested": response.games_ingested,
            "active_models": list(response.active_models),
        }
    except Exception as e:
        return {"healthy": False, "message": str(e)}


def get_active_model(concern: str) -> dict | None:
    """
    Get active model metadata.

    Strategy:
      1. Try gRPC (Training Service) — fast, always up-to-date
      2. Fall back to direct platform.db read — works even if Training is down

    This ensures Analysis Service degrades gracefully.
    """
    # Try gRPC first
    result = get_active_model_grpc(concern)
    if result is not None:
        return result

    # Fallback: read platform.db directly
    try:
        from analysis_service.core import db

        return db.get_active_model(concern)
    except Exception:
        return None
