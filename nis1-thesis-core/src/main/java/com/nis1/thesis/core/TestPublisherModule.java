package com.nis1.thesis.core;
import java.time.Instant;
import java.util.UUID;
import com.nis1.thesis.sdk.*;

/**
 * A test module that publishes a HostAlertData event.
 */
public class TestPublisherModule implements PluggableModule {
    private CoreSystemApi api;

    @Override
    public String getName() {
        return "TestPublisherModule";
    }

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

    @Override
    public void shutdown() {
        System.out.println(getName() + " shutting down.");
    }
}
