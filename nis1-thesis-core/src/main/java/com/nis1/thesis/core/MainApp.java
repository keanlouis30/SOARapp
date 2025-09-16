package com.nis1.thesis.core;

import com.nis1.thesis.sdk.PluggableModule;

/**
 * Main application entry point for the SOAR framework.
 * <p>
 * This class orchestrates the startup and shutdown of all core framework components,
 * including the communication infrastructure, module management, workflow engine,
 * and example security modules. It demonstrates the complete SOAR framework
 * initialization and provides a coordinated shutdown mechanism.
 * </p>
 * <p>
 * The application initializes components in the following order:
 * <ol>
 *   <li>CoreSystemApi - Communication infrastructure</li>
 *   <li>WorkflowEngine - Policy-driven automation engine</li>
 *   <li>ModuleLifecycleManager - Plugin management system</li>
 *   <li>Example security modules - Demonstration implementations</li>
 * </ol>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class MainApp {
    /**
     * Main entry point for the SOAR framework application.
     * <p>
     * Initializes all core components in the proper order, sets up graceful shutdown
     * handling, and keeps the application running until shutdown is requested.
     * The method ensures proper cleanup of all resources during shutdown.
     * </p>
     * 
     * @param args Command line arguments (currently unused)
     * @throws Exception if critical initialization fails (RabbitMQ connection, etc.)
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== SOAR Framework Startup ===");
        
        // Initialize core system API
        CoreSystemApiImpl api = new CoreSystemApiImpl();
        System.out.println("[MainApp] CoreSystemApi initialized");
        
        // Initialize workflow engine
        WorkflowEngine workflowEngine = new WorkflowEngine(api);
        System.out.println("[MainApp] WorkflowEngine initialized");
        
        // Initialize module lifecycle manager
        ModuleLifecycleManager moduleManager = new ModuleLifecycleManager(api);
        System.out.println("[MainApp] ModuleLifecycleManager initialized");
        
        // Load built-in example modules
        loadExampleModules(api);
        
        System.out.println("=== SOAR Framework Started ===");
        System.out.println("System is now running and processing events...");
        System.out.println("Press Ctrl+C to shutdown");
        
        // Set up shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== SOAR Framework Shutdown ===");
            
            try {
                moduleManager.shutdownAllModules();
                workflowEngine.shutdown();
                api.shutdown();
                System.out.println("[MainApp] Shutdown complete");
            } catch (Exception e) {
                System.err.println("[MainApp] Error during shutdown: " + e.getMessage());
            }
        }));
        
        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[MainApp] Main thread interrupted");
        }
    }
    
    /**
     * Loads and initializes example modules to demonstrate the SOAR framework capabilities.
     * <p>
     * This method creates instances of demonstration modules including a test publisher
     * and a Suricata NIDS integration module. These modules showcase different aspects
     * of the framework including event publishing, subscription patterns, and security
     * tool integration.
     * </p>
     * <p>
     * Errors during module loading are logged but don't prevent the application from
     * continuing, demonstrating the fault-tolerant design of the framework.
     * </p>
     * 
     * @param api The CoreSystemApi instance to provide to the loaded modules
     */
    private static void loadExampleModules(CoreSystemApiImpl api) {
        System.out.println("[MainApp] Loading example modules...");
        
        try {
            // Load TestPublisherModule
            PluggableModule testModule = new TestPublisherModule();
            testModule.initialize(api);
            System.out.println("[MainApp] Loaded: " + testModule.getName());
            
            // Load SuricataModule for demonstration
            PluggableModule suricataModule = new SuricataModule();
            suricataModule.initialize(api);
            System.out.println("[MainApp] Loaded: " + suricataModule.getName());
            
            System.out.println("[MainApp] Example modules loaded successfully");
            
        } catch (Exception e) {
            System.err.println("[MainApp] Error loading example modules: " + e.getMessage());
        }
    }
}
