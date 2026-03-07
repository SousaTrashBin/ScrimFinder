FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the entire project — all services share the same image,
# the entrypoint is controlled by docker-compose via CMD override.
COPY . .

# Ensure the models directory exists inside the container
RUN mkdir -p /app/models