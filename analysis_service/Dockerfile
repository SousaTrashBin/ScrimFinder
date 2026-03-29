FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --user --no-cache-dir -r requirements.txt

FROM python:3.11-slim
WORKDIR /app

COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

COPY . ./analysis_service/
COPY tests/ ./tests/
COPY pytest.ini .

RUN find /app -type d -exec touch {}/__init__.py \;

EXPOSE 8001
CMD ["uvicorn", "analysis_service.main:app", "--host", "0.0.0.0", "--port", "8001"]
