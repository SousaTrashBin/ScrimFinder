FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the entire analysis_service package (which includes tests/ inside it)
COPY . ./analysis_service/

COPY pytest.ini .

ENV PYTHONPATH=/app

RUN find /app/analysis_service -type d -exec touch {}/__init__.py \;

RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8001
CMD ["uvicorn", "analysis_service.main:app", "--host", "0.0.0.0", "--port", "8001"]