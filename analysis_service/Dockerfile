FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY .. .
RUN mkdir -p /app/data/models /app/data/games /app/data/datasets
EXPOSE 8000
