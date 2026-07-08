# simulators/pos-worker/Dockerfile

FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY worker.py .
COPY transaction_generator.py .

# Environment variables
ENV WORKER_ID=worker-default
ENV TRANSACTION_RATE=1.0
ENV OFFLINE_MODE=false

# Run the worker
CMD ["python", "worker.py"]