FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . ./analysis_service/
COPY tests/ ./tests/
COPY pytest.ini .

RUN find /app -type d -exec touch {}/__init__.py \;
RUN chown -R 1000:1000 /app

USER 1000

EXPOSE 8001
CMD ["uvicorn", "analysis_service.main:app", "--host", "0.0.0.0", "--port", "8001"]
