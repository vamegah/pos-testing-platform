// framework-core/src/main/java/com/toshiba/pos/model/E2EFlowSteps.java

package com.toshiba.pos.model;

import java.util.*;

/**
 * E2EFlowSteps — defines the transaction flow steps for E2E testing.
 * 
 * <p>This model is used by ProductAdapter to tell the test engine what steps
 * to execute for a full end-to-end transaction.
 */
public class E2EFlowSteps {

    private final List<FlowStep> steps;

    private E2EFlowSteps(List<FlowStep> steps) {
        this.steps = Collections.unmodifiableList(steps);
    }

    public List<FlowStep> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "E2EFlowSteps{steps=" + steps + '}';
    }

    /**
     * A single step in the E2E flow.
     */
    public static class FlowStep {
        private final String id;
        private final String action;
        private final String service;
        private final String endpoint;
        private final Map<String, Object> parameters;

        public FlowStep(String id, String action, String service, String endpoint, Map<String, Object> parameters) {
            this.id = id;
            this.action = action;
            this.service = service;
            this.endpoint = endpoint;
            this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        }

        public String getId() { return id; }
        public String getAction() { return action; }
        public String getService() { return service; }
        public String getEndpoint() { return endpoint; }
        public Map<String, Object> getParameters() { return parameters; }

        @Override
        public String toString() {
            return "FlowStep{id='" + id + "', action='" + action + "', service='" + service + "'}";
        }
    }

    /**
     * Builder for E2EFlowSteps.
     */
    public static class Builder {
        private final List<FlowStep> steps = new ArrayList<>();

        public Builder addStep(String id, String action, String service, String endpoint, Map<String, Object> parameters) {
            steps.add(new FlowStep(id, action, service, endpoint, parameters));
            return this;
        }

        public Builder addStep(String id, String action, String service, String endpoint) {
            return addStep(id, action, service, endpoint, Collections.emptyMap());
        }

        public E2EFlowSteps build() {
            return new E2EFlowSteps(steps);
        }
    }
}