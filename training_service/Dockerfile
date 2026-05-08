FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . ./training_service/
COPY tests/ ./tests/
COPY pytest.ini .
RUN find /app -type d -exec touch {}/__init__.py \;
RUN mkdir -p /app/data/models /app/data/games /app/data/datasets
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8000 50051
CMD ["uvicorn", "training_service.main:app", "--host", "0.0.0.0", "--port", "8000"]