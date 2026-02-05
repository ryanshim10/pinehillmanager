FROM python:3.11-slim AS web

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY web/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY web/main.py ./web/
COPY shared /app/shared

# Ensure __init__.py exists
RUN touch /app/shared/__init__.py
RUN touch /app/shared/connectors/__init__.py

WORKDIR /app/web

EXPOSE 8088

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8088"]
