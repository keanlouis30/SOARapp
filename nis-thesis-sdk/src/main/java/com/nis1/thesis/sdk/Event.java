package com.nis1.thesis.sdk;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic event envelope used across the framework.
 * @param <T> Type of the payload.
 */
public final class Event<T> {
    private final String id;
    private final Instant timestamp;
    private final String type;
    private final T data;

    public Event(String id, Instant timestamp, String type, T data) {
        this.id = Objects.requireNonNull(id, "id");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.type = Objects.requireNonNull(type, "type");
        this.data = Objects.requireNonNull(data, "data");
    }

    /** Convenience factory generating id & timestamp. */
    public static <T> Event<T> of(String type, T data) {
        return new Event<>(UUID.randomUUID().toString(), Instant.now(), type, data);
    }

    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public T getData() { return data; }
}
