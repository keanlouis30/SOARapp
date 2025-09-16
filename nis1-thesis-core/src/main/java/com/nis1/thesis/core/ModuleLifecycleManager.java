package com.nis1.thesis.core;

import com.nis1.thesis.sdk.PluggableModule;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Enumeration;

/**
 * Manages the lifecycle of pluggable modules with fault-tolerant loading and initialization.
 * <p>
 * This class provides comprehensive module management capabilities including dynamic JAR loading,
 * reflection-based module discovery, fault-tolerant initialization, and graceful shutdown.
 * It enables the SOAR framework to load security modules from external JAR files at runtime.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Dynamic JAR file loading and class discovery</li>
 *   <li>Reflection-based PluggableModule instantiation</li>
 *   <li>Fault-tolerant module initialization with isolation</li>
 *   <li>Graceful shutdown with proper resource cleanup</li>
 *   <li>Module lifecycle tracking and management</li>
 * </ul>
 * <p>
 * The manager implements fail-safe behavior where individual module failures do not
 * affect other modules or the core system stability.
 * </p>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class ModuleLifecycleManager {
    private final List<PluggableModule> loadedModules = new ArrayList<>();
    private final CoreSystemApiImpl coreApi;

    /**
     * Constructs a new ModuleLifecycleManager with the specified CoreSystemApi.
     * 
     * @param coreApi The core system API instance to provide to loaded modules
     */
    public ModuleLifecycleManager(CoreSystemApiImpl coreApi) {
        this.coreApi = coreApi;
    }

    /**
     * Scans a directory for JAR files and loads all discoverable modules.
     */
    public void loadModulesFromDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("[ModuleManager] Directory does not exist or is not a directory: " + directory.getPath());
            return;
        }

        File[] jarFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            System.out.println("[ModuleManager] No JAR files found in directory: " + directory.getPath());
            return;
        }

        System.out.println("[ModuleManager] Found " + jarFiles.length + " JAR files to process");

        for (File jarFile : jarFiles) {
            PluggableModule module = loadModuleFromJar(jarFile);
            if (module != null) {
                initializeModule(module);
            }
        }

        System.out.println("[ModuleManager] Successfully loaded " + loadedModules.size() + " modules");
    }

    /**
     * Loads a single module from a JAR file using reflection.
     */
    public PluggableModule loadModuleFromJar(File jarFile) {
        System.out.println("[ModuleManager] Processing JAR: " + jarFile.getName());
        
        try {
            // Create URL class loader for the JAR
            URL[] urls = { new URL("jar:file:" + jarFile.getAbsolutePath() + "!/") };
            URLClassLoader classLoader = URLClassLoader.newInstance(urls);

            // Scan the JAR for classes that implement PluggableModule
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    
                    // Only process .class files
                    if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                        String className = entry.getName()
                            .replace('/', '.')
                            .substring(0, entry.getName().length() - 6); // Remove .class extension
                        
                        try {
                            Class<?> loadedClass = classLoader.loadClass(className);
                            
                            // Check if class implements PluggableModule
                            if (PluggableModule.class.isAssignableFrom(loadedClass) && 
                                !loadedClass.isInterface() && 
                                !java.lang.reflect.Modifier.isAbstract(loadedClass.getModifiers())) {
                                
                                // Create an instance of the module
                                PluggableModule module = (PluggableModule) loadedClass.getDeclaredConstructor().newInstance();
                                System.out.println("[ModuleManager] Found module: " + module.getName() + " (" + className + ")");
                                return module;
                            }
                        } catch (Exception e) {
                            // Continue processing other classes if one fails
                            System.err.println("[ModuleManager] Failed to load class " + className + ": " + e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ModuleManager] Failed to process JAR " + jarFile.getName() + ": " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Initializes a module with fault-tolerant error handling.
     */
    private void initializeModule(PluggableModule module) {
        try {
            System.out.println("[ModuleManager] Initializing module: " + module.getName());
            
            // ** THE CRITICAL FAIL-SAFE **
            // The call to the third-party code is wrapped in a try-catch block.
            module.initialize(coreApi);
            loadedModules.add(module);
            
            System.out.println("[ModuleManager] Successfully initialized: " + module.getName());
            
        } catch (Throwable t) {
            // Catching 'Throwable' is intentional to handle both Exceptions and Errors.
            // If ANY part of the initialization fails for one module,
            // the system logs the error and simply moves on to the next one.
            System.err.println("[ModuleManager] Failed to initialize module " + 
                (module != null ? module.getName() : "unknown") + 
                ". Error: " + t.getMessage());
            t.printStackTrace(); // For debugging purposes
        }
    }

    /**
     * Safely shuts down all loaded modules.
     */
    public void shutdownAllModules() {
        System.out.println("[ModuleManager] Shutting down " + loadedModules.size() + " modules");
        
        for (PluggableModule module : loadedModules) {
            try {
                System.out.println("[ModuleManager] Shutting down: " + module.getName());
                module.shutdown();
            } catch (Throwable t) {
                System.err.println("[ModuleManager] Error shutting down module " + 
                    module.getName() + ": " + t.getMessage());
            }
        }
        
        loadedModules.clear();
        System.out.println("[ModuleManager] All modules shut down");
    }

    /**
     * Returns a copy of the currently loaded modules.
     */
    public List<PluggableModule> getLoadedModules() {
        return new ArrayList<>(loadedModules);
    }

    /**
     * Gets the count of successfully loaded modules.
     */
    public int getModuleCount() {
        return loadedModules.size();
    }
}
