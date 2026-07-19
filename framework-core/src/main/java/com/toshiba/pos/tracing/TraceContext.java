// framework-core/src/main/java/com/toshiba/pos/tracing/TraceContext.java

package com.toshiba.pos.tracing;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TraceContext — Shared utility for correlation-ID generation and propagation.
 * 
 * <p>This utility provides a consistent way to generate and propagate
 * correlation IDs (X-Trace-Id) across all services in the POS test platform.
 * 
 * <p>The Golden Path test (24.2) uses this to trace transactions through
 * every hop in the architecture.
 */
public class TraceContext {

    private static final String TRACE_HEADER = "X-Trace-Id";

    private static final ThreadLocal<String> currentTraceId = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, TraceSpan> activeSpans = new ConcurrentHashMap<>();

    /**
     * Generate a new trace ID.
     * 
     * @return A unique trace ID string
     */
    public static String generateTraceId() {
        return "TRACE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get the current trace ID, generating one if none exists.
     * 
     * @return The current trace ID
     */
    public static String getTraceId() {
        String traceId = currentTraceId.get();
        if (traceId == null) {
            traceId = generateTraceId();
            currentTraceId.set(traceId);
        }
        return traceId;
    }

    /**
     * Set the current trace ID.
     * 
     * @param traceId The trace ID to set
     */
    public static void setTraceId(String traceId) {
        currentTraceId.set(traceId);
    }

    /**
     * Clear the current trace ID.
     */
    public static void clear() {
        currentTraceId.remove();
    }

    /**
     * Get the trace header name.
     * 
     * @return The trace header name (X-Trace-Id)
     */
    public static String getTraceHeader() {
        return TRACE_HEADER;
    }

    /**
     * Start a new span for a service hop.
     * 
     * @param serviceName The name of the service
     * @return The span ID
     */
    public static String startSpan(String serviceName) {
        String traceId = getTraceId();
        String spanId = "span-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        activeSpans.put(spanId, new TraceSpan(traceId, serviceName, spanId, System.currentTimeMillis()));
        return spanId;
    }

    /**
     * End a span.
     * 
     * @param spanId The span ID to end
     */
    public static void endSpan(String spanId) {
        TraceSpan span = activeSpans.get(spanId);
        if (span != null) {
            span.setEndTime(System.currentTimeMillis());
            // Could log or report the span here
        }
    }

    /**
     * Get the trace header value for HTTP requests.
     * 
     * @return The trace header name and value as a string
     */
    public static String getTraceHeaderValue() {
        return getTraceHeader() + ": " + getTraceId();
    }

    /**
     * TraceSpan — Represents a single service hop in a trace.
     */
    public static class TraceSpan {
        private final String traceId;
        private final String serviceName;
        private final String spanId;
        private final long startTime;
        private long endTime;

        public TraceSpan(String traceId, String serviceName, String spanId, long startTime) {
            this.traceId = traceId;
            this.serviceName = serviceName;
            this.spanId = spanId;
            this.startTime = startTime;
        }

        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public String getSpanId() { return spanId; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDuration() { return endTime - startTime; }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        @Override
        public String toString() {
            return "TraceSpan{traceId='" + traceId + "', service='" + serviceName + "', spanId='" + spanId + "', duration=" + getDuration() + "ms}";
        }
    }
}