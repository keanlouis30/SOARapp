package com.nis1.thesis.core;

/**
 * Minimal abstraction for publishing standardized JSON messages to a message broker (e.g., RabbitMQ).
 * Implement this interface in your RabbitMQ client and pass the instance into ModuleLifecycleManager.
 */
public interface JsonMessagePublisher {
    /**
     * Publish a JSON message with the given event type as the routing key.
     *
     * @param eventType routing key (e.g., system.modules.loaded)
     * @param json fully-formed JSON payload to send
     */
    void publish(String eventType, String json);
}
