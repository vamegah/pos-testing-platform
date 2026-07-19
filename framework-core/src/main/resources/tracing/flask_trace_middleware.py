# framework-core/src/main/resources/tracing/flask_trace_middleware.py
# Trace Context Middleware for Flask Services

"""
Trace Context Middleware for Flask Services.

This module provides middleware for Flask applications to automatically
propagate X-Trace-Id headers and log trace context.

Usage:
    from flask_trace_middleware import TraceMiddleware
    app = Flask(__name__)
    TraceMiddleware(app)
"""

import logging
from flask import request, g
import uuid
import time

logger = logging.getLogger(__name__)

TRACE_HEADER = "X-Trace-Id"


class TraceMiddleware:
    """Flask middleware for trace context propagation."""

    def __init__(self, app):
        self.app = app
        self._setup_middleware()

    def _setup_middleware(self):
        @self.app.before_request
        def before_request():
            # Extract or generate trace ID
            trace_id = request.headers.get(TRACE_HEADER)
            if not trace_id:
                trace_id = f"TRACE-{int(time.time()*1000)}-{uuid.uuid4().hex[:8]}"

            g.trace_id = trace_id
            g.start_time = time.time()

            # Log trace start
            logger.info(f"Trace started: {trace_id} for {request.path}")

        @self.app.after_request
        def after_request(response):
            # Add trace header to response
            if hasattr(g, "trace_id"):
                response.headers[TRACE_HEADER] = g.trace_id

                # Log trace completion
                if hasattr(g, "start_time"):
                    duration = (time.time() - g.start_time) * 1000
                    logger.info(f"Trace completed: {g.trace_id} in {duration:.2f}ms")

            return response
