package com.nis1.thesis.core;

import com.nis1.thesis.sdk.CoreSystemApi;
import com.nis1.thesis.sdk.PluggableModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Enumeration;

/**
 * Module Registry & Lifecycle Manager
 * <p>
 * This component is the administrative backbone of the framework, responsible for the
 * "plug-and-play" nature of the modules. It manages the entire lifecycle of a module
 * from discovery to shutdown.
 * </p>
 * <p>
 * Its function is to dynamically discover, initialize, and gracefully terminate all
 * Pluggable Modules. It ensures that each module is properly integrated into the framework
 * and provided with the necessary tools to operate.
 * </p>
 * <p>
 * Upon startup, the Lifecycle Manager scans a designated directory for available module
 * packages (e.g., .jar files). For each discovered module, it uses reflection to instantiate
 * the class that implements the PluggableModule interface. It then calls the module's
 * initialize() method, passing it the CoreSystemAPI handle. This handle is the module's
 * sole connection to the rest of the system. The manager is also responsible for registering
 * the module's event subscriptions with the RabbitMQ broker.
 * </p>
 * <p>
 * The Lifecycle Manager automates the process of system extension. It transforms the
 * architectural concept of modularity into a practical reality, allowing users to add or
 * update capabilities simply by dropping a new file into a folder, thereby lowering the
 * barrier to entry for developing and deploying new security functions.
 * </p>
 * <p>
 * Upon startup, after successfully loading modules, it sends a standardized JSON message
 * to the RabbitMQ client containing information about all properly loaded modules.
 * </p>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class ModuleLifecycleManager {
    /** The collection of successfully loaded and initialized modules */
    private final List<PluggableModule> loadedModules = new ArrayList<>();
    
    /** The core API that will be passed to each module during initialization */
    private final CoreSystemApi coreApi;
    
    /** JSON message publisher for RabbitMQ integration */
    private final JsonMessagePublisher messagePublisher;

    /** Default modules directory path */
    private static final String DEFAULT_MODULES_DIR = "modules";
    
    /** JSON serialization */
    private final Gson gson;

    /**
     * Constructs a new ModuleLifecycleManager with the provided CoreSystemApi and message publisher.
     * <p>
     * This constructor is used when the CoreSystemApi is already initialized elsewhere.
     * The provided API will be passed to each module during its initialization.
     * </p>
     * 
     * @param coreApi The CoreSystemApi instance to pass to modules during initialization
     * @param messagePublisher The JSON message publisher for RabbitMQ integration
     */
    public ModuleLifecycleManager(CoreSystemApi coreApi, JsonMessagePublisher messagePublisher) {
        this.coreApi = coreApi;
        this.messagePublisher = messagePublisher;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("[ModuleLifecycleManager] Initialized with provided CoreSystemApi and RabbitMQ publisher");
    }

    /**
     * Legacy constructor for backward compatibility with existing MainApp.
     * <p>
     * This constructor accepts only a CoreSystemApi and uses null for the message publisher.
     * It's provided for compatibility with existing code that doesn't use RabbitMQ messaging.
     * </p>
     * 
     * @param coreApi The CoreSystemApi instance to pass to modules during initialization
     */
    public ModuleLifecycleManager(CoreSystemApi coreApi) {
        this.coreApi = coreApi;
        this.messagePublisher = null;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("[ModuleLifecycleManager] Initialized with CoreSystemApi (no RabbitMQ publisher)");
    }
    
    /**
     * Default constructor that creates a new instance with no external dependencies.
     * <p>
     * This is provided as a fallback for testing and development purposes only.
     * Modules initialized through a manager constructed this way will receive null
     * for their CoreSystemApi parameter, and no messages will be sent to RabbitMQ.
     * </p>
     * <p>
     * For production use, always use the constructor that accepts a CoreSystemApi and JsonMessagePublisher.
     * </p>
     */
    public ModuleLifecycleManager() {
        this.coreApi = null;
        this.messagePublisher = null;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("[ModuleLifecycleManager] Warning: Initialized without CoreSystemApi or RabbitMQ publisher");
        System.out.println("[ModuleLifecycleManager] Modules will not have access to the event system");
    }
    
    /**
     * Scans the default modules directory for JAR files and loads all discoverable modules.
     * <p>
     * This method scans the 'modules' directory (relative to the current working directory)
     * for JAR files, extracts and instantiates any classes that implement the PluggableModule
     * interface, and initializes them.
     * </p>
     * <p>
     * If the modules directory doesn't exist or contains no JAR files, this method will
     * log appropriate messages but not throw exceptions.
     * </p>
     */
    public void loadModulesFromDirectory() {
        loadModulesFromDirectory(new File(DEFAULT_MODULES_DIR));
    }
    
    /**
     * Scans a specified directory for JAR files and loads all discoverable modules.
     * <p>
     * This method examines each JAR file in the specified directory, looking for classes
     * that implement the PluggableModule interface. For each valid module class found,
     * it instantiates the class, initializes it with the CoreSystemApi, and adds it to
     * the registry of loaded modules.
     * </p>
     * <p>
     * The process is fault-tolerant: if one module fails to load or initialize, the
     * system continues processing other modules. Failures are logged but don't prevent
     * other modules from loading.
     * </p>
     * 
     * @param directory The directory to scan for JAR files containing modules
     */
    public void loadModulesFromDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("[ModuleLifecycleManager] Directory does not exist or is not a directory: " 
                    + directory.getPath());
            return;
        }

        File[] jarFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            System.out.println("[ModuleLifecycleManager] No JAR files found in directory: " 
                    + directory.getPath());
            return;
        }

        System.out.println("[ModuleLifecycleManager] Found " + jarFiles.length + " JAR files to process");

        for (File jarFile : jarFiles) {
            try {
                PluggableModule module = loadModuleFromJar(jarFile);
                if (module != null) {
                    initializeModule(module);
                }
            } catch (Exception e) {
                System.err.println("[ModuleLifecycleManager] Error processing JAR " 
                        + jarFile.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("[ModuleLifecycleManager] Successfully loaded " 
                + loadedModules.size() + " modules");
        
        // Send startup message to RabbitMQ after successful module loading
        sendModulesLoadedMessage();
    }

    /**
     * Loads a single module from a JAR file using reflection.
     * <p>
     * This method uses a URLClassLoader to load classes from the specified JAR file,
     * then searches for classes that implement the PluggableModule interface. It creates
     * an instance of the first such class found.
     * </p>
     * <p>
     * The method is fault-tolerant: if a class cannot be loaded or instantiated, it logs
     * the error and continues processing other classes in the JAR.
     * </p>
     * 
     * @param jarFile The JAR file to process
     * @return An instantiated PluggableModule, or null if none was found
     * @throws Exception If the JAR file cannot be processed
     */
    private PluggableModule loadModuleFromJar(File jarFile) throws Exception {
        System.out.println("[ModuleLifecycleManager] Processing JAR: " + jarFile.getName());
        
        // Create URL class loader for the JAR
        URL[] urls = { new URL("jar:file:" + jarFile.getAbsolutePath() + "!/") };
        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls)) {

            // Scan the JAR for classes that implement PluggableModule
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    
                    // Only process .class files, skip inner classes
                    if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                        String className = entry.getName()
                            .replace('/', '.')
                            .substring(0, entry.getName().length() - 6); // Remove .class extension
                        
                        try {
                            Class<?> loadedClass = classLoader.loadClass(className);
                            
                            // Check if class implements PluggableModule and can be instantiated
                            if (PluggableModule.class.isAssignableFrom(loadedClass) && 
                                !loadedClass.isInterface() && 
                                !java.lang.reflect.Modifier.isAbstract(loadedClass.getModifiers())) {
                                
                                // Create an instance of the module
                                PluggableModule module = (PluggableModule) loadedClass.getDeclaredConstructor().newInstance();
                                System.out.println("[ModuleLifecycleManager] Found module: " + module.getName() + 
                                    " (" + className + ")");
                                return module;
                            }
                        } catch (Exception e) {
                            // Continue processing other classes if one fails
                            System.err.println("[ModuleLifecycleManager] Failed to load class " + 
                                className + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        System.out.println("[ModuleLifecycleManager] No valid PluggableModule found in JAR: " + 
            jarFile.getName());
        return null;
    }

    /**
     * Initializes a module with fault-tolerant error handling.
     * <p>
     * This method calls the module's initialize() method, passing it the CoreSystemApi.
     * If initialization succeeds, the module is added to the registry of loaded modules.
     * If initialization fails, the error is logged but doesn't prevent other modules
     * from being initialized.
     * </p>
     * 
     * @param module The module to initialize
     */
    private void initializeModule(PluggableModule module) {
        try {
            System.out.println("[ModuleLifecycleManager] Initializing module: " + module.getName());
            
            // ** THE CRITICAL FAIL-SAFE **
            // The call to the third-party code is wrapped in a try-catch block.
            module.initialize(coreApi);
            loadedModules.add(module);
            
            System.out.println("[ModuleLifecycleManager] Successfully initialized: " + module.getName());
            
        } catch (Throwable t) {
            // Catching 'Throwable' is intentional to handle both Exceptions and Errors.
            // If ANY part of the initialization fails for one module,
            // the system logs the error and simply moves on to the next one.
            System.err.println("[ModuleLifecycleManager] Failed to initialize module " + 
                (module != null ? module.getName() : "unknown") + 
                ". Error: " + t.getMessage());
            t.printStackTrace(); // For debugging purposes
        }
    }

    /**
     * Safely shuts down all loaded modules.
     * <p>
     * This method iterates through all successfully loaded modules and calls their
     * shutdown() method to allow proper resource cleanup. If a module's shutdown process
     * throws an exception, it is caught and logged, but doesn't prevent other modules
     * from shutting down.
     * </p>
     * <p>
     * After all modules have been shut down (or attempts made to shut them down),
     * the registry of loaded modules is cleared.
     * </p>
     */
    public void shutdownAllModules() {
        System.out.println("[ModuleLifecycleManager] Shutting down " + loadedModules.size() + " modules");
        
        for (PluggableModule module : loadedModules) {
            try {
                System.out.println("[ModuleLifecycleManager] Shutting down: " + module.getName());
                module.shutdown();
            } catch (Throwable t) {
                System.err.println("[ModuleLifecycleManager] Error shutting down module " + 
                    module.getName() + ": " + t.getMessage());
                t.printStackTrace(); // For debugging purposes
            }
        }
        
        loadedModules.clear();
        System.out.println("[ModuleLifecycleManager] All modules shut down");
    }

    /**
     * Gets the count of successfully loaded and initialized modules.
     * 
     * @return The number of currently loaded modules
     */
    public int getModuleCount() {
        return loadedModules.size();
    }
    
    /**
     * Returns a copy of the currently loaded modules.
     * <p>
     * This method returns a defensive copy of the loaded modules list to prevent
     * external modification of the internal registry.
     * </p>
     * 
     * @return A new list containing all currently loaded modules
     */
    public List<PluggableModule> getLoadedModules() {
        return new ArrayList<>(loadedModules);
    }
    
    /**
     * Sends a standardized JSON message to RabbitMQ containing information about all successfully loaded modules.
     * <p>
     * This method creates a JSON message in the standardized SOAR format and sends it to the
     * RabbitMQ client using the configured message publisher. The message contains details
     * about all modules that were successfully loaded and initialized during startup.
     * </p>
     * <p>
     * The message follows the standardized format:
     * <pre>
     * {
     *   "event_id": "unique-uuid",
     *   "timestamp": "2025-09-22T00:27:11.000Z",
     *   "event_type": "system.modules.loaded",
     *   "source_module": "ModuleLifecycleManager",
     *   "payload": {
     *     "modules_count": 3,
     *     "loaded_modules": [
     *       {
     *         "name": "WazuhModule",
     *         "status": "active",
     *         "load_time": "2025-09-22T00:27:10.500Z"
     *       }
     *     ]
     *   }
     * }
     * </pre>
     * </p>
     */
    private void sendModulesLoadedMessage() {
        if (messagePublisher == null) {
            System.out.println("[ModuleLifecycleManager] No message publisher configured - skipping RabbitMQ notification");
            return;
        }
        
        try {
            // Create standardized message structure
            Map<String, Object> message = new HashMap<>();
            message.put("event_id", UUID.randomUUID().toString());
            message.put("timestamp", Instant.now().toString());
            message.put("event_type", "system.modules.loaded");
            message.put("source_module", "ModuleLifecycleManager");
            
            // Create payload with module information
            Map<String, Object> payload = new HashMap<>();
            payload.put("modules_count", loadedModules.size());
            payload.put("startup_time", Instant.now().toString());
            payload.put("status", "completed");
            
            // Add detailed module information
            List<Map<String, Object>> modulesList = new ArrayList<>();
            for (PluggableModule module : loadedModules) {
                Map<String, Object> moduleInfo = new HashMap<>();
                moduleInfo.put("name", module.getName());
                moduleInfo.put("status", "active");
                moduleInfo.put("load_time", Instant.now().toString()); // In practice, you'd store actual load time
                moduleInfo.put("class_name", module.getClass().getName());
                modulesList.add(moduleInfo);
            }
            payload.put("loaded_modules", modulesList);
            
            message.put("payload", payload);
            
            // Convert to JSON and publish
            String json = gson.toJson(message);
            messagePublisher.publish("system.modules.loaded", json);
            
            System.out.println("[ModuleLifecycleManager] Sent modules loaded notification to RabbitMQ");
            System.out.println("[ModuleLifecycleManager] Message: " + json);
            
        } catch (Exception e) {
            System.err.println("[ModuleLifecycleManager] Failed to send modules loaded message to RabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
