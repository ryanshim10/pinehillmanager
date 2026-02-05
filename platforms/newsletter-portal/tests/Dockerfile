FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy test requirements first
COPY tests/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY web/main.py ./web/
COPY shared /app/shared
COPY tests /app/tests

# Ensure __init__.py exists
RUN touch /app/shared/__init__.py
RUN touch /app/shared/connectors/__init__.py

WORKDIR /app
