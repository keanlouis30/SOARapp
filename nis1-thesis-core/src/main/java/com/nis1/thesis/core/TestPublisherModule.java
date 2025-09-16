package com.nis1.thesis.core;
import java.time.Instant;
import java.util.UUID;
import com.nis1.thesis.sdk.*;

/**
 * Test module that demonstrates event publishing capabilities in the SOAR framework.
 * <p>
 * This module serves as a simple example of how to implement a PluggableModule
 * and publish events to the communication fabric. It generates a sample host alert
 * event during initialization to demonstrate the event publishing mechanism.
 * </p>
 * <p>
 * This module is primarily used for testing, demonstration, and validation
 * of the core event publishing infrastructure.
 * </p>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class TestPublisherModule implements PluggableModule {
    private CoreSystemApi api;

    /**
     * Returns the name of this test module.
     * 
     * @return The module name for identification and logging purposes
     */
    @Override
    public String getName() {
        return "TestPublisherModule";
    }

    /**
     * Initializes the test module and publishes a sample host alert event.
     * <p>
     * Creates a demonstration host alert with simulated suspicious activity
     * and publishes it through the CoreSystemApi to demonstrate the event
     * publishing mechanism and message routing capabilities.
     * </p>
     * 
     * @param api The core system API for event publishing and subscription
     */
    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        HostAlertData alert = new HostAlertData("10.0.0.1", "Suspicious login", "HIGH");
        Event<HostAlertData> event = new Event<>(
                UUID.randomUUID().toString(),   // unique ID
                Instant.now(),                  // current timestamp
                "alerts.host.wazuh",            // type
                alert                           // payload
        );
        api.publishEvent(event);
    }

    /**
     * Performs cleanup during module shutdown.
     * <p>
     * Since this is a simple test module with no ongoing operations or
     * allocated resources, the shutdown process only logs the shutdown event.
     * </p>
     */
    @Override
    public void shutdown() {
        System.out.println(getName() + " shutting down.");
    }
}
