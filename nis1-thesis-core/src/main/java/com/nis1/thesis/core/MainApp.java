package com.nis1.thesis.core;

import com.nis1.thesis.sdk.PluggableModule;

/**
 * Simple main app that wires CoreSystemApiImpl and TestPublisherModule together.
 */
public class MainApp {
    public static void main(String[] args) throws Exception {
        CoreSystemApiImpl api = new CoreSystemApiImpl();
        PluggableModule module = new TestPublisherModule();

        module.initialize(api);
        module.shutdown();
    }
}
