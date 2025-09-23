# Enhanced ModuleLifecycleManager with RabbitMQ Integration

## Overview

The `ModuleLifecycleManager` has been enhanced to send standardized JSON messages to your RabbitMQ client after successfully loading modules. This provides real-time notification of module loading status and detailed information about all active modules.

## Key Features

### 1. **Plug-and-Play Module Loading**
- Scans designated directory for .jar files
- Uses URLClassLoader and Java Reflection to dynamically load modules
- Fault-tolerant: individual module failures don't prevent system startup
- Instantiates classes implementing `PluggableModule` interface

### 2. **Standardized JSON Messaging**
- Sends JSON messages in your specified format upon startup completion
- Contains detailed information about all successfully loaded modules
- Integrates with your RabbitMQ client via the `JsonMessagePublisher` interface

### 3. **External RabbitMQ Integration**
- Works with your existing RabbitMQ client implementation
- No internal RabbitMQ dependencies - uses simple interface abstraction
- Flexible integration pattern allows any message broker

## JSON Message Format

Upon successful module loading, the ModuleLifecycleManager sends this standardized JSON message:

```json
{
  "event_id": "e721dc1a-f34a-4c9e-ae82-1827b72e9a1e",
  "timestamp": "2025-09-22T00:27:11.452Z",
  "event_type": "system.modules.loaded",
  "source_module": "ModuleLifecycleManager",
  "payload": {
    "modules_count": 3,
    "startup_time": "2025-09-22T00:27:11.452Z",
    "status": "completed",
    "loaded_modules": [
      {
        "name": "WazuhModule",
        "status": "active",
        "load_time": "2025-09-22T00:27:10.500Z",
        "class_name": "com.nis1.thesis.udm.WazuhModule"
      },
      {
        "name": "SuricataModule",
        "status": "active", 
        "load_time": "2025-09-22T00:27:10.800Z",
        "class_name": "com.nis1.thesis.udm.SuricataModule"
      },
      {
        "name": "ThreatIntelModule",
        "status": "active",
        "load_time": "2025-09-22T00:27:11.100Z", 
        "class_name": "com.nis1.thesis.udm.ThreatIntelModule"
      }
    ]
  }
}
```

## Integration Pattern

### 1. Implement JsonMessagePublisher Interface

Create your RabbitMQ client that implements the simple interface:

```java
public class YourRabbitMqClient implements JsonMessagePublisher {
    private Channel channel;
    
    public YourRabbitMqClient(Channel channel) {
        this.channel = channel;
    }
    
    @Override
    public void publish(String eventType, String json) {
        try {
            channel.basicPublish("soar_exchange", eventType, null, json.getBytes());
            System.out.println("Published: " + eventType);
        } catch (IOException e) {
            System.err.println("Failed to publish: " + e.getMessage());
        }
    }
}
```

### 2. Initialize ModuleLifecycleManager

```java
// Create your RabbitMQ client
JsonMessagePublisher rabbitClient = new YourRabbitMqClient(channel);

// Create CoreSystemApi (or use existing one)
CoreSystemApi coreApi = new YourCoreSystemApi();

// Create ModuleLifecycleManager with RabbitMQ integration
ModuleLifecycleManager manager = new ModuleLifecycleManager(coreApi, rabbitClient);

// Load modules - this automatically sends notification to RabbitMQ
manager.loadModulesFromDirectory();
```

## Constructor Options

### 1. **Production Constructor**
```java
public ModuleLifecycleManager(CoreSystemApi coreApi, JsonMessagePublisher messagePublisher)
```
- **Recommended for production use**
- Provides modules with CoreSystemApi for event communication
- Sends JSON messages to your RabbitMQ client

### 2. **Legacy Constructor** 
```java
public ModuleLifecycleManager(CoreSystemApi coreApi)
```
- **Backward compatibility** with existing MainApp
- Provides modules with CoreSystemApi
- **No RabbitMQ messaging** (messagePublisher is null)

### 3. **Testing Constructor**
```java
public ModuleLifecycleManager()
```
- **Development/testing only**
- No CoreSystemApi (modules receive null)
- No RabbitMQ messaging

## Module Loading Process

### Startup Phase (executed once on application start)

1. **Directory Scan**: Identifies all .jar files in modules directory
2. **JAR Processing**: For each JAR file:
   - Creates URLClassLoader for dynamic loading
   - Scans classes using Java Reflection
   - Finds classes implementing PluggableModule interface
3. **Module Instantiation**: Creates instances via default constructor
4. **Module Initialization**: Calls `initialize(CoreSystemApi)` method
5. **Registry Update**: Adds successful modules to internal registry
6. **RabbitMQ Notification**: Sends JSON message with loading results

### Shutdown Phase (executed on application termination)

1. **Module Iteration**: Iterates through all loaded modules
2. **Cleanup**: Calls `shutdown()` method on each module
3. **Registry Clear**: Clears internal module registry

## Error Handling

The ModuleLifecycleManager implements comprehensive fault-tolerance:

- **JAR Processing Errors**: Logged, processing continues with other JARs
- **Class Loading Errors**: Individual failures don't prevent other classes from loading
- **Module Initialization Errors**: Failed modules logged, others continue initializing
- **Shutdown Errors**: Module shutdown failures logged, others continue shutting down
- **RabbitMQ Errors**: Message publishing failures logged, don't prevent module operation

## Usage Examples

### Standalone Application
```java
public class MyModuleManager {
    public static void main(String[] args) throws Exception {
        // Your RabbitMQ client implementation
        JsonMessagePublisher rabbitClient = new MyRabbitClient();
        CoreSystemApi coreApi = new MyCoreSystemApi();
        
        // Create manager with RabbitMQ integration
        ModuleLifecycleManager manager = new ModuleLifecycleManager(coreApi, rabbitClient);
        
        // Load modules and automatically notify RabbitMQ
        manager.loadModulesFromDirectory();
        
        System.out.println("Loaded " + manager.getModuleCount() + " modules");
    }
}
```

### Integration with Existing MainApp
The existing MainApp continues to work unchanged:
```java
// Existing code continues to work
ModuleLifecycleManager moduleManager = new ModuleLifecycleManager(api);
moduleManager.loadModulesFromDirectory();
// No RabbitMQ messages sent (no publisher configured)
```

## Benefits

1. **Automated Notifications**: Automatic JSON message generation and publishing
2. **External Integration**: Works with your existing RabbitMQ infrastructure
3. **Standardized Format**: Consistent JSON structure matching your specification
4. **Fault Tolerance**: Robust error handling ensures system reliability
5. **Backward Compatibility**: Existing code continues to work unchanged
6. **Flexible Architecture**: Simple interface allows different message broker implementations

## Monitoring and Debugging

The ModuleLifecycleManager provides comprehensive logging:

```
[ModuleLifecycleManager] Found 3 JAR files to process
[ModuleLifecycleManager] Processing JAR: wazuh-module.jar
[ModuleLifecycleManager] Found module: WazuhModule (com.nis1.thesis.udm.WazuhModule)
[ModuleLifecycleManager] Initializing module: WazuhModule
[ModuleLifecycleManager] Successfully initialized: WazuhModule
[ModuleLifecycleManager] Successfully loaded 3 modules
[ModuleLifecycleManager] Sent modules loaded notification to RabbitMQ
[ModuleLifecycleManager] Message: {...JSON message...}
```

This enhanced ModuleLifecycleManager provides the exact functionality you requested: automatic module loading with standardized JSON messaging to your RabbitMQ client, while maintaining the robust fault-tolerant design of the original implementation.