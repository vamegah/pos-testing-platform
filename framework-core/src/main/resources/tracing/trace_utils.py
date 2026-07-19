# framework-core/src/main/resources/tracing/trace_utils.py
# Shared trace utilities for Python services

"""
Shared trace utilities for Python Flask services.

Provides:
  - Trace ID extraction from headers
  - Trace ID generation
  - Response header injection
"""

import uuid
import time
import logging
from functools import wraps

logger = logging.getLogger(__name__)
TRACE_HEADER = "X-Trace-Id"


def get_trace_id_from_request(request):
    """Extract trace ID from request headers, or generate new one."""
    trace_id = request.headers.get(TRACE_HEADER)
    if not trace_id:
        trace_id = f"TRACE-{int(time.time()*1000)}-{uuid.uuid4().hex[:8]}"
    return trace_id


def add_trace_header(response, trace_id):
    """Add trace header to response."""
    response.headers[TRACE_HEADER] = trace_id
    return response


def trace_logger(service_name):
    """Create a logger that includes trace ID in the log format."""
    # This would be extended with a custom log filter
    return logger
