#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-}"

if [ -z "$PYTHON_BIN" ]; then
    if command -v python3 >/dev/null 2>&1; then
        PYTHON_BIN="python3"
    else
        PYTHON_BIN="python"
    fi
fi

if [ "$#" -eq 0 ]; then
    services="analysis_service training_service"
else
    services="$*"
fi

for service in $services; do
    proto_file="$ROOT_DIR/$service/proto/training_service.proto"

    if [ ! -f "$proto_file" ]; then
        echo "skipping gRPC generation for $service: missing proto file ($proto_file)"
        continue
    fi

    echo "generating Python gRPC stubs for $service..."
    "$PYTHON_BIN" -m grpc_tools.protoc \
        -I "$ROOT_DIR/$service/proto" \
        --python_out="$ROOT_DIR/$service" \
        --grpc_python_out="$ROOT_DIR/$service" \
        "$proto_file"

    "$PYTHON_BIN" -c "import sys; from pathlib import Path; path = Path(sys.argv[1]); service = sys.argv[2]; text = path.read_text(); path.write_text(text.replace('import training_service_pb2 as training__service__pb2', f'from {service} import training_service_pb2 as training__service__pb2'))" "$ROOT_DIR/$service/training_service_pb2_grpc.py" "$service"
done
