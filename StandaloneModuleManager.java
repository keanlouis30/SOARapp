package com.nis1.thesis.core;

import com.nis1.thesis.sdk.CoreSystemApi;

/**
 * Example standalone application demonstrating ModuleLifecycleManager 
 * with RabbitMQ integration.
 * 
 * This shows how to use the ModuleLifecycleManager independently 
 * of the MainApp, with your own RabbitMQ client implementation.
 */
public class StandaloneModuleManager {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Standalone Module Manager Startup ===");
            
            // Create your RabbitMQ client (implement JsonMessagePublisher interface)
            JsonMessagePublisher rabbitMqClient = new MyRabbitMqClient();
            
            // Create a simple CoreSystemApi implementation (or use your existing one)
            CoreSystemApi coreApi = new SimpleCoreSystemApi();
            
            // Create ModuleLifecycleManager with RabbitMQ integration
            ModuleLifecycleManager moduleManager = new ModuleLifecycleManager(coreApi, rabbitMqClient);
            
            // Load modules from the modules directory
            System.out.println("[StandaloneModuleManager] Loading modules from directory...");
            moduleManager.loadModulesFromDirectory();
            
            // The ModuleLifecycleManager will automatically send a JSON message
            // to your RabbitMQ client with details about loaded modules
            
            System.out.println("[StandaloneModuleManager] Modules loaded: " + moduleManager.getModuleCount());
            System.out.println("=== Standalone Module Manager Running ===");
            System.out.println("Press Ctrl+C to shutdown");
            
            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== Standalone Module Manager Shutdown ===");
                moduleManager.shutdownAllModules();
                System.out.println("[StandaloneModuleManager] Shutdown complete");
            }));
            
            // Keep application running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("[StandaloneModuleManager] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example implementation of JsonMessagePublisher for RabbitMQ integration.
     * Replace this with your actual RabbitMQ client implementation.
     */
    private static class MyRabbitMqClient implements JsonMessagePublisher {
        
        public MyRabbitMqClient() {
            System.out.println("[MyRabbitMqClient] Initialized RabbitMQ client");
        }
        
        @Override
        public void publish(String eventType, String json) {
            // This is where you would implement your actual RabbitMQ publishing logic
            System.out.println("[MyRabbitMqClient] Publishing to RabbitMQ:");
            System.out.println("[MyRabbitMqClient] Event Type: " + eventType);
            System.out.println("[MyRabbitMqClient] JSON Payload: " + json);
            
            // Example of what your implementation might look like:
            /*
            try {
                channel.basicPublish("soar_exchange", eventType, null, json.getBytes());
                System.out.println("[MyRabbitMqClient] Message published successfully");
            } catch (IOException e) {
                System.err.println("[MyRabbitMqClient] Failed to publish message: " + e.getMessage());
            }
            */
        }
    }
    
    /**
     * Simple CoreSystemApi implementation for demonstration.
     * Replace this with your actual CoreSystemApi implementation.
     */
    private static class SimpleCoreSystemApi implements CoreSystemApi {
        
        @Override
        public void publishEvent(com.nis1.thesis.sdk.Event<?> event) {
            System.out.println("[SimpleCoreSystemApi] Published event: " + event.getType());
        }
        
        @Override
        public void subscribeToEvent(String eventType, java.util.function.Consumer<com.nis1.thesis.sdk.Event<?>> listener) {
            System.out.println("[SimpleCoreSystemApi] Subscribed to event type: " + eventType);
        }
    }
}