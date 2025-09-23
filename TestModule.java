package com.example.test;

import com.nis1.thesis.sdk.*;

/**
 * Simple test module to demonstrate the ModuleLifecycleManager functionality.
 * This module can be compiled into a JAR and placed in the modules directory
 * to test the dynamic loading capabilities.
 */
public class TestModule implements PluggableModule {
    private CoreSystemApi api;
    
    @Override
    public String getName() {
        return "Test Dynamic Module";
    }
    
    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        System.out.println("[TestModule] Successfully initialized dynamic test module!");
        
        if (api != null) {
            // Subscribe to some events for demonstration
            api.subscribeToEvent("test.events.*", this::handleTestEvent);
            System.out.println("[TestModule] Subscribed to test events");
        } else {
            System.out.println("[TestModule] Warning: No API provided");
        }
    }
    
    @Override
    public void shutdown() {
        System.out.println("[TestModule] Shutting down test module");
    }
    
    private void handleTestEvent(Event<?> event) {
        System.out.println("[TestModule] Received event: " + event.getType());
    }
}