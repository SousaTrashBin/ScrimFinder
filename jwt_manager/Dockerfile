FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . ./jwt_manager/

COPY pytest.ini .

ENV PYTHONPATH=/app

RUN find /app/jwt_manager -type d -exec touch {}/__init__.py \;

RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8002
CMD ["uvicorn", "jwt_manager.main:app", "--host", "0.0.0.0", "--port", "8002"]