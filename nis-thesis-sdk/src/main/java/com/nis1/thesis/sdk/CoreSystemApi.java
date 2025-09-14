package com.nis1.thesis.sdk;

/**
 * API exposed by the Core System to all pluggable modules.
 */
public interface CoreSystemApi {
    void publishEvent(Event<?> event);
}
