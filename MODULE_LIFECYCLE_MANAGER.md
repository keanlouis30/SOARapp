# ModuleLifecycleManager Implementation

## Overview

The `ModuleLifecycleManager` is the administrative backbone of the SOAR framework, responsible for the "plug-and-play" nature of the modules. It manages the entire lifecycle of a module from discovery to shutdown.

## Key Features

- **Dynamic JAR Discovery**: Automatically scans directories for JAR files containing modules
- **Reflection-based Loading**: Uses Java reflection to instantiate PluggableModule implementations
- **Fault-tolerant Initialization**: Individual module failures don't prevent system startup
- **Graceful Shutdown**: Proper resource cleanup during system termination
- **Module Registry**: Maintains registry of successfully loaded modules

## Architecture

The ModuleLifecycleManager transforms the architectural concept of modularity into a practical reality, allowing users to add or update capabilities simply by dropping a new JAR file into the modules folder.

### Startup Process

1. **Directory Scan**: Scans designated directory for .jar files
2. **JAR Processing**: For each JAR file found:
   - Creates URLClassLoader for dynamic loading
   - Scans all classes using reflection
   - Identifies classes implementing PluggableModule interface
3. **Module Instantiation**: Creates instances of valid module classes
4. **Initialization**: Calls `initialize()` method with CoreSystemApi handle
5. **Registration**: Adds successful modules to internal registry

### Shutdown Process

1. **Module Iteration**: Iterates through all loaded modules
2. **Cleanup**: Calls `shutdown()` method on each module
3. **Registry Clear**: Clears the internal module registry

## Usage Examples

### Basic Usage

```java
// Create CoreSystemApi instance
CoreSystemApiImpl api = new CoreSystemApiImpl();

// Create ModuleLifecycleManager with API
ModuleLifecycleManager manager = new ModuleLifecycleManager(api);

// Load modules from default 'modules' directory
manager.loadModulesFromDirectory();

// Get count of loaded modules
System.out.println("Loaded " + manager.getModuleCount() + " modules");

// Shutdown all modules when done
manager.shutdownAllModules();
```

### Custom Directory Loading

```java
// Load from specific directory
File customDir = new File("/path/to/custom/modules");
manager.loadModulesFromDirectory(customDir);
```

### Integration with MainApp

```java
public class MainApp {
    public static void main(String[] args) throws Exception {
        // Initialize core components
        CoreSystemApiImpl api = new CoreSystemApiImpl();
        WorkflowEngine workflowEngine = new WorkflowEngine(api);
        ModuleLifecycleManager moduleManager = new ModuleLifecycleManager(api);
        
        // Load external modules
        moduleManager.loadModulesFromDirectory();
        
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            moduleManager.shutdownAllModules();
            workflowEngine.shutdown();
            api.shutdown();
        }));
        
        // Keep application running
        Thread.currentThread().join();
    }
}
```

## Directory Structure

The default modules directory structure:

```
SOARapp/
├── modules/                          # Default modules directory
│   ├── wazuh-module.jar             # Example Wazuh integration
│   ├── suricata-module.jar          # Example Suricata integration
│   └── custom-security-module.jar   # Custom security module
├── nis1-thesis-core/                # Core framework
└── nis-thesis-sdk/                  # SDK for module development
```

## Error Handling

The ModuleLifecycleManager implements comprehensive fault-tolerance:

- **JAR Processing Errors**: Logged but don't stop processing other JARs
- **Class Loading Errors**: Individual class failures don't prevent other classes from loading
- **Module Initialization Errors**: Failed modules are logged but don't prevent other modules from initializing
- **Shutdown Errors**: Module shutdown failures are logged but don't prevent other modules from shutting down

## Implementation Details

### Constructor Options

1. **With CoreSystemApi**: `ModuleLifecycleManager(CoreSystemApi api)`
   - Production constructor
   - Passes API to modules during initialization

2. **Default Constructor**: `ModuleLifecycleManager()`
   - For testing/development only
   - Modules receive null API (may cause NullPointerExceptions)

### Key Methods

- `loadModulesFromDirectory()`: Load from default 'modules' directory
- `loadModulesFromDirectory(File directory)`: Load from specified directory
- `shutdownAllModules()`: Gracefully shutdown all loaded modules
- `getModuleCount()`: Get count of successfully loaded modules
- `getLoadedModules()`: Get defensive copy of loaded modules list

### Logging Format

All log messages use the `[ModuleLifecycleManager]` prefix for easy identification and filtering.

## Module Development

To create a module that can be loaded by the ModuleLifecycleManager:

1. **Implement Interface**: Implement the `PluggableModule` interface
2. **Provide Constructor**: Include a public no-argument constructor
3. **Package as JAR**: Create a JAR file containing your module
4. **Deploy**: Place JAR in the modules directory

Example module:

```java
package com.example.security;

import com.nis1.thesis.sdk.*;

public class ExampleSecurityModule implements PluggableModule {
    private CoreSystemApi api;
    
    @Override
    public String getName() {
        return "Example Security Module";
    }
    
    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        // Subscribe to events, start services, etc.
    }
    
    @Override
    public void shutdown() {
        // Clean up resources
    }
}
```

## Testing

You can test the ModuleLifecycleManager by:

1. Creating a simple test module JAR
2. Placing it in the modules directory
3. Running the SOAR framework
4. Observing the logs for successful loading and initialization

The framework provides comprehensive logging to help debug any issues with module loading or initialization.