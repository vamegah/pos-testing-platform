# simulators/gateway/simulator.py

"""
API Gateway & Integration Layer (D1)

Lightweight local gateway in front of the cloud microservices:
  - Routing: Routes requests to appropriate services
  - Mock auth-token check: Validates authentication
  - Basic rate-limiting: Limits requests per client
  - Event-routing (pub/sub): Routes events to subscribers

Requests without a valid mock token are rejected.
Rate limit trips after N requests.
An event published by one service is observed by a subscriber via the gateway.
"""

import os
import json
import logging
import time
import threading
import requests
from datetime import datetime, timedelta
from typing import Dict, Any, Optional, List
from collections import defaultdict
from flask import Flask, request, jsonify, abort, g

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# ============================================================
# Configuration
# ============================================================
# Service URLs
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")
PAYMENT_URL = os.environ.get("PAYMENT_GATEWAY_URL", "http://payment-gateway:8084")
ORDER_PROCESSING_URL = os.environ.get(
    "ORDER_PROCESSING_URL", "http://order-processing:8085"
)

# Rate limiting
RATE_LIMIT_WINDOW_SECONDS = int(os.environ.get("RATE_LIMIT_WINDOW_SECONDS", 60))
RATE_LIMIT_MAX_REQUESTS = int(os.environ.get("RATE_LIMIT_MAX_REQUESTS", 100))

# Auth
VALID_TOKENS = {
    "valid-token-123": {"client_id": "client-001", "role": "pos"},
    "valid-token-456": {"client_id": "client-002", "role": "admin"},
    "valid-token-789": {"client_id": "client-003", "role": "customer"},
}

# ============================================================
# State
# ============================================================
# Rate limiting state: client_id -> list of request timestamps
rate_limit_state = defaultdict(list)

# Event subscribers: event_type -> list of callback URLs
event_subscribers = defaultdict(list)

# Event history
event_history = []

# ============================================================
# Helper Functions
# ============================================================


def extract_auth_token() -> Optional[str]:
    """Extract auth token from request headers."""
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        return auth_header[7:]
    return None


def validate_token(token: str) -> Optional[Dict[str, Any]]:
    """Validate an auth token."""
    return VALID_TOKENS.get(token)


def check_rate_limit(client_id: str) -> bool:
    """Check if a client has exceeded rate limit."""
    now = time.time()
    window_start = now - RATE_LIMIT_WINDOW_SECONDS

    # Clean old entries
    rate_limit_state[client_id] = [
        ts for ts in rate_limit_state[client_id] if ts >= window_start
    ]

    if len(rate_limit_state[client_id]) >= RATE_LIMIT_MAX_REQUESTS:
        return False

    rate_limit_state[client_id].append(now)
    return True


def publish_event(event_type: str, event_data: Dict[str, Any]):
    """Publish an event to all subscribers."""
    event = {
        "event_id": len(event_history) + 1,
        "event_type": event_type,
        "event_data": event_data,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "source": "gateway",
    }
    event_history.append(event)
    logger.info(f"Event published: {event_type} (id={event['event_id']})")

    # Notify subscribers
    subscribers = event_subscribers.get(event_type, [])
    for subscriber_url in subscribers:
        try:
            response = requests.post(f"{subscriber_url}/event", json=event, timeout=2)
            if response.status_code == 200:
                logger.debug(f"Event delivered to subscriber: {subscriber_url}")
            else:
                logger.warning(
                    f"Event delivery failed to {subscriber_url}: {response.status_code}"
                )
        except Exception as e:
            logger.warning(f"Event delivery error to {subscriber_url}: {e}")


def route_request(
    service_url: str, path: str, method: str, headers: Dict, body: Optional[Dict]
) -> requests.Response:
    """Route a request to a backend service."""
    url = f"{service_url}{path}"
    logger.debug(f"Routing {method} {url}")

    try:
        if method.upper() == "GET":
            response = requests.get(url, headers=headers, timeout=5)
        elif method.upper() == "POST":
            response = requests.post(url, json=body, headers=headers, timeout=5)
        elif method.upper() == "PUT":
            response = requests.put(url, json=body, headers=headers, timeout=5)
        elif method.upper() == "DELETE":
            response = requests.delete(url, headers=headers, timeout=5)
        else:
            return None
        return response
    except Exception as e:
        logger.error(f"Route error: {e}")
        return None


# ============================================================
# Middleware
# ============================================================


@app.before_request
def auth_middleware():
    """Authentication middleware."""
    # Skip auth for health endpoints
    if request.path in ["/health", "/gateway/health"]:
        return

    token = extract_auth_token()
    if not token:
        abort(401, description="Missing authorization token")

    client = validate_token(token)
    if not client:
        abort(401, description="Invalid authorization token")

    # Set client info for downstream use
    g.client_id = client["client_id"]
    g.client_role = client["role"]


@app.before_request
def rate_limit_middleware():
    """Rate limiting middleware."""
    # Skip rate limiting for health endpoints and event endpoints
    if request.path in [
        "/health",
        "/gateway/health",
        "/gateway/events",
        "/gateway/events/subscribe",
    ]:
        return

    client_id = getattr(g, "client_id", "unknown")
    if not check_rate_limit(client_id):
        abort(429, description="Rate limit exceeded. Try again later.")


# ============================================================
# Endpoints
# ============================================================


@app.route("/health", methods=["GET"])
@app.route("/gateway/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return (
        jsonify(
            {
                "status": "healthy",
                "service": "api-gateway",
                "active_clients": len(rate_limit_state),
                "rate_limit_window_seconds": RATE_LIMIT_WINDOW_SECONDS,
                "rate_limit_max_requests": RATE_LIMIT_MAX_REQUESTS,
                "event_subscribers": {k: len(v) for k, v in event_subscribers.items()},
                "event_history_count": len(event_history),
            }
        ),
        200,
    )


@app.route("/gateway/auth/token", methods=["POST"])
def create_token():
    """
    Create a new auth token (mock).

    Expected payload:
    {
        "client_id": "client-001",
        "role": "pos"
    }
    """
    data = request.get_json() or {}
    client_id = data.get("client_id", "client-001")
    role = data.get("role", "pos")

    token = f"valid-token-{len(VALID_TOKENS) + 1}"
    VALID_TOKENS[token] = {"client_id": client_id, "role": role}

    return (
        jsonify(
            {
                "status": "created",
                "token": token,
                "client_id": client_id,
                "role": role,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/gateway/events/subscribe", methods=["POST"])
def subscribe_event():
    """
    Subscribe to an event type.

    Expected payload:
    {
        "event_type": "transaction.completed",
        "subscriber_url": "http://subscriber:8080"
    }
    """
    data = request.get_json() or {}
    event_type = data.get("event_type")
    subscriber_url = data.get("subscriber_url")

    if not event_type:
        abort(400, description="Missing 'event_type' field")
    if not subscriber_url:
        abort(400, description="Missing 'subscriber_url' field")

    if subscriber_url not in event_subscribers[event_type]:
        event_subscribers[event_type].append(subscriber_url)

    return (
        jsonify(
            {
                "status": "subscribed",
                "event_type": event_type,
                "subscriber_url": subscriber_url,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/gateway/events", methods=["GET"])
def get_events():
    """Get event history."""
    limit = request.args.get("limit", 50, type=int)
    event_type = request.args.get("event_type")

    events = event_history
    if event_type:
        events = [e for e in events if e["event_type"] == event_type]

    events = events[-limit:]

    return (
        jsonify(
            {
                "events": events,
                "count": len(events),
                "total": len(event_history),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/gateway/events/publish", methods=["POST"])
def publish_event_endpoint():
    """
    Publish an event via the gateway.

    Expected payload:
    {
        "event_type": "transaction.completed",
        "event_data": {"transaction_id": "txn-001", "amount": 10.00}
    }
    """
    data = request.get_json() or {}
    event_type = data.get("event_type")
    event_data = data.get("event_data", {})

    if not event_type:
        abort(400, description="Missing 'event_type' field")

    publish_event(event_type, event_data)

    return (
        jsonify(
            {
                "status": "published",
                "event_type": event_type,
                "event_data": event_data,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/gateway/rate-limit/status", methods=["GET"])
def rate_limit_status():
    """Get rate limit status."""
    client_id = getattr(g, "client_id", "unknown")
    window_start = time.time() - RATE_LIMIT_WINDOW_SECONDS
    recent_requests = [ts for ts in rate_limit_state[client_id] if ts >= window_start]

    return (
        jsonify(
            {
                "client_id": client_id,
                "window_seconds": RATE_LIMIT_WINDOW_SECONDS,
                "max_requests": RATE_LIMIT_MAX_REQUESTS,
                "requests_in_window": len(recent_requests),
                "remaining": max(0, RATE_LIMIT_MAX_REQUESTS - len(recent_requests)),
                "reset_seconds": int(
                    window_start + RATE_LIMIT_WINDOW_SECONDS - time.time()
                ),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


# ============================================================
# Route endpoints (proxy to backend services)
# ============================================================


@app.route(
    "/gateway/api/<service>/<path:path>", methods=["GET", "POST", "PUT", "DELETE"]
)
def proxy_request(service: str, path: str):
    """Proxy request to backend service."""
    service_map = {
        "pricing": PRICING_URL,
        "promotions": PROMOTIONS_URL,
        "tax": TAX_URL,
        "payment": PAYMENT_URL,
        "order": ORDER_PROCESSING_URL,
    }

    if service not in service_map:
        abort(404, description=f"Unknown service: {service}")

    service_url = service_map[service]
    method = request.method
    headers = {
        k: v
        for k, v in request.headers.items()
        if k.lower() not in ["host", "authorization"]
    }
    body = request.get_json(silent=True)

    # Add trace headers
    headers["X-Request-ID"] = request.headers.get(
        "X-Request-ID", f"req-{int(time.time())}"
    )
    headers["X-Client-ID"] = getattr(g, "client_id", "unknown")

    response = route_request(service_url, f"/{path}", method, headers, body)

    if response is None:
        abort(503, description="Backend service unavailable")

    # Publish event for completed transactions
    if service == "payment" and "/authorize" in path and response.status_code == 200:
        try:
            data = response.json()
            if data.get("status") == "approved":
                publish_event(
                    "payment.completed",
                    {
                        "transaction_id": data.get("transaction_id"),
                        "amount": data.get("amount"),
                        "client_id": getattr(g, "client_id", "unknown"),
                    },
                )
        except:
            pass

    return jsonify(response.json()), response.status_code


@app.route("/gateway/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run a test scenario for the gateway.

    Expected payload:
    {
        "scenario": "auth_success" | "auth_failure" | "rate_limit" | "event_pubsub"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "auth_success")
    results = {}

    # Helper: make a request with auth
    def make_request_with_auth(token, path="/gateway/api/pricing/price/SKU-1001"):
        headers = {"Authorization": f"Bearer {token}"}
        try:
            response = requests.get(
                f"http://localhost:{port}{path}", headers=headers, timeout=2
            )
            return response.status_code
        except:
            return None

    if scenario == "auth_success":
        status = make_request_with_auth("valid-token-123")
        results["auth_success"] = {"status_code": status, "passed": status == 200}

    elif scenario == "auth_failure":
        status = make_request_with_auth("invalid-token")
        results["auth_failure"] = {"status_code": status, "passed": status == 401}

    elif scenario == "rate_limit":
        # Make many requests to trigger rate limit
        statuses = []
        client_token = "valid-token-123"
        for i in range(RATE_LIMIT_MAX_REQUESTS + 10):
            status = make_request_with_auth(client_token)
            statuses.append(status)
            if status == 429:
                break

        rate_limited = 429 in statuses
        results["rate_limit"] = {
            "requests_made": len(statuses),
            "statuses": statuses[:10],
            "rate_limited": rate_limited,
            "passed": rate_limited,
        }

    elif scenario == "event_pubsub":
        # Subscribe to an event
        subscribe_response = requests.post(
            f"http://localhost:{port}/gateway/events/subscribe",
            json={
                "event_type": "test.event",
                "subscriber_url": "http://localhost:9999/mock-subscriber",
            },
            timeout=2,
        )
        subscribed = subscribe_response.status_code == 200

        # Publish an event
        publish_response = requests.post(
            f"http://localhost:{port}/gateway/events/publish",
            json={"event_type": "test.event", "event_data": {"test": "data"}},
            timeout=2,
        )
        published = publish_response.status_code == 200

        # Check event history
        events_response = requests.get(
            f"http://localhost:{port}/gateway/events", timeout=2
        )
        events_updated = events_response.status_code == 200

        results["event_pubsub"] = {
            "subscribed": subscribed,
            "published": published,
            "events_updated": events_updated,
            "passed": subscribed and published and events_updated,
        }

    else:
        abort(400, description=f"Unknown scenario: {scenario}")

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "gateway_scenarios",
                "scenario": scenario,
                "results": results,
                "summary": {
                    "total": len(results),
                    "passed": sum(
                        1 for r in results.values() if r.get("passed", False)
                    ),
                    "all_passed": all_passed,
                },
            }
        ),
        200,
    )


# ============================================================
# Main
# ============================================================

port = int(os.environ.get("PORT", 5014))

if __name__ == "__main__":
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"API Gateway starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
