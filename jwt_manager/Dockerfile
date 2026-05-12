FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . ./jwt_manager/
RUN find /app -type d -exec touch {}/__init__.py \;
RUN useradd -m -u 1000 appuser && chown -R appuser /app
USER appuser
EXPOSE 8002
CMD ["uvicorn", "jwt_manager.main:app", "--host", "0.0.0.0", "--port", "8002"]