package com.nis1.thesis.core;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standardized JSON event message structure for SOAR framework communication.
 * <p>
 * This class represents the standardized JSON format used for all inter-module
 * communication and RabbitMQ messaging within the SOAR framework. It ensures
 * consistent message structure across all security modules and tools.
 * </p>
 * <p>
 * The JSON format follows the specification:
 * <pre>
 * {
 *   "event_id": "e721dc1a-f34a-4c9e-ae82-1827b72e9a1e",
 *   "timestamp": "2025-07-21T10:35:12.452Z",
 *   "event_type": "alerts.host.wazuh",
 *   "source_module": "WazuhConnector",
 *   "payload": { ... }
 * }
 * </pre>
 * </p>
 *
 * @author NIS1
 * @version 1.0
 * @since September 22, 2025
 */
public class StandardizedEventMessage {
    
    @SerializedName("event_id")
    private String eventId;
    
    @SerializedName("timestamp")
    private String timestamp;
    
    @SerializedName("event_type")
    private String eventType;
    
    @SerializedName("source_module")
    private String sourceModule;
    
    @SerializedName("payload")
    private Map<String, Object> payload;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public StandardizedEventMessage() {
    }
    
    /**
     * Constructs a new standardized event message.
     * 
     * @param eventId Unique identifier for the event
     * @param timestamp ISO 8601 formatted timestamp
     * @param eventType Event type for routing (e.g., "alerts.host.wazuh")
     * @param sourceModule Name of the originating module
     * @param payload Event-specific data payload
     */
    public StandardizedEventMessage(String eventId, String timestamp, String eventType, 
                                   String sourceModule, Map<String, Object> payload) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.sourceModule = sourceModule;
        this.payload = payload;
    }
    
    /**
     * Factory method to create a standardized message from framework Event.
     * 
     * @param event The framework Event to convert
     * @param sourceModule Name of the module that created this event
     * @return A new StandardizedEventMessage
     */
    public static StandardizedEventMessage fromEvent(com.nis1.thesis.sdk.Event<?> event, String sourceModule) {
        return new StandardizedEventMessage(
            event.getId(),
            event.getTimestamp().toString(),
            event.getType(),
            sourceModule,
            convertPayloadToMap(event.getData())
        );
    }
    
    /**
     * Factory method to create a standardized message with auto-generated ID and timestamp.
     * 
     * @param eventType Event type for routing
     * @param sourceModule Name of the originating module
     * @param payload Event-specific data payload
     * @return A new StandardizedEventMessage
     */
    public static StandardizedEventMessage create(String eventType, String sourceModule, Map<String, Object> payload) {
        return new StandardizedEventMessage(
            UUID.randomUUID().toString(),
            Instant.now().toString(),
            eventType,
            sourceModule,
            payload
        );
    }
    
    /**
     * Converts various payload types to a Map for JSON serialization.
     * 
     * @param data The payload data to convert
     * @return A Map representation of the payload
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertPayloadToMap(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        } else {
            // For complex objects, we'll use reflection to convert to Map
            // This is a simplified implementation - in production you might want
            // to use a more sophisticated object-to-map converter
            return Map.of("data", data.toString());
        }
    }
    
    // Getters and Setters
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getSourceModule() {
        return sourceModule;
    }
    
    public void setSourceModule(String sourceModule) {
        this.sourceModule = sourceModule;
    }
    
    public Map<String, Object> getPayload() {
        return payload;
    }
    
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
    
    @Override
    public String toString() {
        return "StandardizedEventMessage{" +
                "eventId='" + eventId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", eventType='" + eventType + '\'' +
                ", sourceModule='" + sourceModule + '\'' +
                ", payload=" + payload +
                '}';
    }
}