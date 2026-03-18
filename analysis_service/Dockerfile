# analysis_service/Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . ./analysis_service/
COPY tests/ ./tests/
COPY pytest.ini .
RUN find /app -type d -exec touch {}/__init__.py \;
EXPOSE 8001