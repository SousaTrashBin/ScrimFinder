FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . ./analysis_service/
COPY tests/ ./tests/
COPY pytest.ini .

RUN python -m grpc_tools.protoc \
    -I analysis_service/proto \
    --python_out=analysis_service \
    --grpc_python_out=analysis_service \
    analysis_service/proto/training_service.proto \
    && python -c "from pathlib import Path; p = Path('analysis_service/training_service_pb2_grpc.py'); p.write_text(p.read_text().replace('import training_service_pb2 as training__service__pb2', 'from analysis_service import training_service_pb2 as training__service__pb2'))"

RUN find /app -type d -exec touch {}/__init__.py \;
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8001
CMD ["uvicorn", "analysis_service.main:app", "--host", "0.0.0.0", "--port", "8001"]
