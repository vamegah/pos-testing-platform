# simulators/pos-services/payment/Dockerfile

FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

# Environment variables
ENV PORT=8084
ENV DEBUG=false

# Expose the port
EXPOSE 8084

# Run the application
CMD ["python", "app.py"]