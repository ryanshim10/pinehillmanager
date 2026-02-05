FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY worker/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY worker/main.py ./worker/
COPY shared /app/shared

# Ensure __init__.py exists
RUN touch /app/shared/__init__.py
RUN touch /app/shared/connectors/__init__.py

WORKDIR /app/worker

CMD ["python", "main.py"]
