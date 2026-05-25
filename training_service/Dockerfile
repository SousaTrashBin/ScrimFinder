FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . ./training_service/
COPY tests/ ./tests/
COPY pytest.ini .
RUN python -m grpc_tools.protoc \
    -I training_service/proto \
    --python_out=training_service \
    --grpc_python_out=training_service \
    training_service/proto/training_service.proto \
    && python -c "from pathlib import Path; p = Path('training_service/training_service_pb2_grpc.py'); p.write_text(p.read_text().replace('import training_service_pb2 as training__service__pb2', 'from training_service import training_service_pb2 as training__service__pb2'))"
RUN find /app -type d -exec touch {}/__init__.py \;
RUN mkdir -p /app/data/models /app/data/games /app/data/datasets
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8000 50051
CMD ["uvicorn", "training_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
