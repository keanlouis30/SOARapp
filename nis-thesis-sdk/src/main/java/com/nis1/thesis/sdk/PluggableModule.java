package com.nis1.thesis.sdk;

/**
 * Contract for any pluggable module.
 */
public interface PluggableModule {
    /**
     * @return a stable, human-readable name for logging/diagnostics.
     */
    String getName();

    /**
     * Called once to provide the core API handle and let the module initialize resources.
     * @param api Core system API to publish events and (in later phases) subscribe.
     */
    void initialize(CoreSystemApi api);

    /**
     * Called on graceful shutdown for resource cleanup.
     */
    void shutdown();
}
