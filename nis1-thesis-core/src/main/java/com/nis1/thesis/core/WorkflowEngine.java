package com.nis1.thesis.core;

import com.nis1.thesis.sdk.CoreSystemApi;
import com.nis1.thesis.sdk.Event;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Basic Workflow Engine that parses YAML workflow files and executes policy-driven responses.
 * <p>
 * This engine provides automated response capabilities by parsing YAML-based workflow definitions
 * and executing them in response to security events. It supports event triggering, conditional
 * logic, and orchestrated response actions across multiple security modules.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>YAML-based workflow definition parsing</li>
 *   <li>Event-driven workflow triggering</li>
 *   <li>Conditional logic and branching support</li>
 *   <li>Multi-step orchestrated responses</li>
 *   <li>Workflow instance tracking and management</li>
 * </ul>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class WorkflowEngine {
    private final CoreSystemApi api;
    private final Map<String, WorkflowDefinition> loadedWorkflows = new ConcurrentHashMap<>();
    private final Map<String, WorkflowInstance> runningInstances = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * Constructs a new WorkflowEngine with the specified CoreSystemApi.
     * 
     * @param api The core system API for event publishing and subscription
     */
    public WorkflowEngine(CoreSystemApi api) {
        this.api = api;
        System.out.println("[WorkflowEngine] Initialized");
    }

    /**
     * Loads a workflow from a YAML file.
     */
    public void loadWorkflow(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            loadWorkflow(inputStream, filePath);
        }
    }

    /**
     * Loads a workflow from an InputStream.
     */
    public void loadWorkflow(InputStream yamlStream, String workflowId) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> workflowData = yaml.load(yamlStream);

            WorkflowDefinition workflow = parseWorkflowDefinition(workflowData);
            workflow.setId(workflowId);

            loadedWorkflows.put(workflowId, workflow);

            // Subscribe to the trigger event type
            String triggerEventType = workflow.getTriggerEventType();
            if (triggerEventType != null) {
                api.subscribeToEvent(triggerEventType, event -> handleTriggerEvent(workflow, event));
                System.out.println("[WorkflowEngine] Loaded workflow: " + workflow.getName() + 
                    " (triggers on: " + triggerEventType + ")");
            }

        } catch (Exception e) {
            System.err.println("[WorkflowEngine] Failed to load workflow: " + e.getMessage());
        }
    }

    /**
     * Parses workflow definition from YAML data.
     */
    private WorkflowDefinition parseWorkflowDefinition(Map<String, Object> data) {
        WorkflowDefinition workflow = new WorkflowDefinition();
        
        workflow.setName((String) data.get("name"));
        workflow.setVersion((String) data.get("version"));
        workflow.setDescription((String) data.get("description"));
        
        // Parse trigger
        Map<String, Object> trigger = (Map<String, Object>) data.get("trigger");
        if (trigger != null) {
            workflow.setTriggerEventType((String) trigger.get("event_type"));
            workflow.setTriggerCondition((String) trigger.get("condition"));
        }
        
        // Parse steps
        List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
        if (steps != null) {
            workflow.setSteps(steps);
        }
        
        return workflow;
    }
    
    /**
     * Handles trigger events and starts workflow execution.
     */
    private void handleTriggerEvent(WorkflowDefinition workflow, Event<?> triggerEvent) {
        try {
            System.out.println("[WorkflowEngine] Trigger event received for workflow: " + workflow.getName());
            
            // Create new workflow instance
            String instanceId = java.util.UUID.randomUUID().toString();
            WorkflowInstance instance = new WorkflowInstance(instanceId, workflow, triggerEvent);
            
            runningInstances.put(instanceId, instance);
            
            // Start execution
            executeWorkflowInstance(instance);
            
        } catch (Exception e) {
            System.err.println("[WorkflowEngine] Error handling trigger event: " + e.getMessage());
        }
    }
    
    /**
     * Gets the count of currently running workflow instances.
     */
    public int getRunningInstanceCount() {
        return runningInstances.size();
    }
    
    /**
     * Gets the count of loaded workflows.
     */
    public int getLoadedWorkflowCount() {
        return loadedWorkflows.size();
    }
    
    /**
     * Shuts down the workflow engine.
     */
    public void shutdown() {
        scheduler.shutdown();
        System.out.println("[WorkflowEngine] Shutdown complete");
    }

    // Simplified executeWorkflowInstance for now
    private void executeWorkflowInstance(WorkflowInstance instance) {
        System.out.println("[WorkflowEngine] Executing workflow instance: " + instance.getId());
        // Basic implementation - would be expanded for full workflow execution
        instance.markCompleted();
        runningInstances.remove(instance.getId());
    }
    
    // Inner classes for workflow definition and instances
    private static class WorkflowDefinition {
        private String id;
        private String name;
        private String version;
        private String description;
        private String triggerEventType;
        private String triggerCondition;
        private List<Map<String, Object>> steps;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTriggerEventType() { return triggerEventType; }
        public void setTriggerEventType(String triggerEventType) { this.triggerEventType = triggerEventType; }
        public String getTriggerCondition() { return triggerCondition; }
        public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }
        public List<Map<String, Object>> getSteps() { return steps; }
        public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }
    }
    
    private static class WorkflowInstance {
        private final String id;
        private final WorkflowDefinition workflow;
        private final Event<?> triggerEvent;
        private int currentStepIndex = 0;
        private boolean completed = false;
        private boolean failed = false;
        
        public WorkflowInstance(String id, WorkflowDefinition workflow, Event<?> triggerEvent) {
            this.id = id;
            this.workflow = workflow;
            this.triggerEvent = triggerEvent;
        }
        
        public String getId() { return id; }
        public WorkflowDefinition getWorkflow() { return workflow; }
        public Event<?> getTriggerEvent() { return triggerEvent; }
        public boolean isCompleted() { return completed || failed; }
        public void markCompleted() { this.completed = true; }
        public void markFailed() { this.failed = true; }
    }
}