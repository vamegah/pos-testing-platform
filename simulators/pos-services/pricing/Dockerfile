# simulators/pos-services/pricing/Dockerfile

FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

# Environment variables
ENV PORT=8081
ENV DEBUG=false

# Expose the port
EXPOSE 8081

# Run the application
CMD ["python", "app.py"]