# Testing the ModuleLifecycleManager

## Quick Test Steps

### 1. Compile the Test Module

```bash
# Navigate to the project root
cd /home/kean/Documents/SOARapp

# Compile the test module with SDK on classpath
javac -cp "nis-thesis-sdk/target/classes:nis1-thesis-core/target/classes" TestModule.java

# Create the JAR file
mkdir -p modules
jar cf modules/test-module.jar -C . com/example/test/TestModule.class
```

### 2. Run the SOAR Framework

```bash
# Make sure RabbitMQ is running
docker start thesis-rmq

# Compile the entire project first
mvn clean compile

# Run the main application
cd nis1-thesis-core
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp"
```

### 3. Expected Output

When you run the framework, you should see output similar to:

```
=== SOAR Framework Startup ===
[CoreSystemApi] Connected to RabbitMQ
[MainApp] CoreSystemApi initialized
[WorkflowEngine] Initialized
[MainApp] WorkflowEngine initialized
[ModuleLifecycleManager] Initialized with provided CoreSystemApi
[MainApp] ModuleLifecycleManager initialized
[MainApp] Loading example modules...
[TestPublisherModule] ... (existing modules)
[MainApp] Loading external modules from JAR files...
[ModuleLifecycleManager] Found 1 JAR files to process
[ModuleLifecycleManager] Processing JAR: test-module.jar
[ModuleLifecycleManager] Found module: Test Dynamic Module (com.example.test.TestModule)
[ModuleLifecycleManager] Initializing module: Test Dynamic Module
[TestModule] Successfully initialized dynamic test module!
[TestModule] Subscribed to test events
[CoreSystemApi] Subscribed to: test.events.*
[ModuleLifecycleManager] Successfully initialized: Test Dynamic Module
[ModuleLifecycleManager] Successfully loaded 1 modules
[MainApp] External module loading complete. Total modules loaded by manager: 1
=== SOAR Framework Started ===
```

## Alternative Test: Create a More Complex Module

### 1. Create a Custom Security Module

```java
package com.company.security;

import com.nis1.thesis.sdk.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CustomSecurityModule implements PluggableModule {
    private CoreSystemApi api;
    private ScheduledExecutorService scheduler;
    private boolean running = false;
    
    @Override
    public String getName() {
        return "Custom Security Scanner v1.0";
    }
    
    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = true;
        
        System.out.println("[CustomSecurityModule] Initializing custom security scanner");
        
        // Subscribe to host alerts
        api.subscribeToEvent("alerts.host.*", this::handleHostAlert);
        
        // Start periodic security scan
        scheduler.scheduleAtFixedRate(this::performSecurityScan, 0, 30, TimeUnit.SECONDS);
        
        System.out.println("[CustomSecurityModule] Custom security module ready");
    }
    
    @Override
    public void shutdown() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        System.out.println("[CustomSecurityModule] Security scanner shutdown complete");
    }
    
    private void handleHostAlert(Event<?> event) {
        System.out.println("[CustomSecurityModule] Processing host alert: " + event.getType());
    }
    
    private void performSecurityScan() {
        if (running) {
            System.out.println("[CustomSecurityModule] Performing periodic security scan...");
        }
    }
}
```

### 2. Compile and Package

```bash
# Compile
javac -cp "nis-thesis-sdk/target/classes:nis1-thesis-core/target/classes" -d temp_classes CustomSecurityModule.java

# Create JAR
jar cf modules/custom-security-module.jar -C temp_classes .

# Clean up
rm -rf temp_classes
```

## Troubleshooting

### Module Not Loading

1. **Check JAR is in modules directory**: `ls -la modules/`
2. **Verify JAR contains class files**: `jar tf modules/your-module.jar`
3. **Check compilation classpath**: Make sure SDK is on classpath during compilation
4. **Review logs**: Look for specific error messages in the console output

### Module Initialization Fails

1. **Check constructor**: Module must have public no-argument constructor
2. **Review dependencies**: Module may be missing required dependencies
3. **API usage**: If using CoreSystemApi, check for null pointer exceptions

### Common Issues

- **ClassNotFoundException**: Module JAR is not properly constructed
- **NoSuchMethodException**: Missing public no-argument constructor
- **NullPointerException**: Module trying to use API when it's null (test mode)

## Verification Commands

```bash
# Check if modules directory exists and contains JARs
ls -la modules/

# Verify JAR contents
jar tf modules/test-module.jar

# Check if SOAR framework can find the modules
cd nis1-thesis-core
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp" 2>&1 | grep ModuleLifecycleManager
```

## Expected Behavior

1. **Fault Tolerance**: If one module fails, others should still load
2. **Graceful Shutdown**: Ctrl+C should trigger proper shutdown of all modules
3. **Event Subscriptions**: Modules should be able to subscribe to and receive events
4. **Logging**: All operations should be logged with `[ModuleLifecycleManager]` prefix

## Performance Notes

- Module loading is synchronous during startup
- Each JAR is scanned completely for PluggableModule implementations
- First valid module found in each JAR is instantiated
- Failed modules don't prevent system startup

This testing approach validates the complete "plug-and-play" functionality of the ModuleLifecycleManager.