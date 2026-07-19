#!/bin/bash
# scripts/up.sh
# Convenience script to start docker-compose with different profiles

set -euo pipefail

PROFILE="${1:-full}"

case "$PROFILE" in
    api)
        echo "Starting API-only services..."
        docker compose -f docker-compose.yml -f docker-compose.api.yml up -d
        ;;
    ui)
        echo "Starting UI services..."
        docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d
        ;;
    architecture)
        echo "Starting architecture services..."
        docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml up -d
        ;;
    full)
        echo "Starting full stack..."
        docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml -f docker-compose.ui.yml up -d
        ;;
    *)
        echo "Usage: $0 {api|ui|architecture|full}"
        exit 1
        ;;
esac

echo "✅ Services started with profile: $PROFILE"