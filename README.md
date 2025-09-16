# SOAR Framework - Security Orchestration, Automation and Response

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.0+-orange.svg)](https://www.rabbitmq.com/)

A modular SOAR (Security Orchestration, Automation and Response) framework built with Java 21 and Maven. The project implements an event-driven architecture with RabbitMQ message queuing for security event processing and automated response workflows.

## Architecture Overview

The SOAR framework follows a **multi-module Maven structure** with a **plugin-based architecture**:

### Modules
- **SDK Module** (`nis-thesis-sdk`): Core API interfaces and data models that define the pluggable module contract
- **Core Module** (`nis1-thesis-core`): Implementation of the core system and example modules
- **Root Project**: Parent POM coordinating all modules

### Key Design Patterns
- **Plugin Architecture**: Uses `PluggableModule` interface for extensible security modules
- **Event-Driven Architecture**: All communication through typed `Event<T>` objects
- **Message Queue Integration**: RabbitMQ for reliable event publishing with topic exchanges

### Core Components
- `CoreSystemApi`: Interface for modules to publish/subscribe to events
- `Event<T>`: Generic event envelope with metadata and typed payload
- `PluggableModule`: Contract for all security processing modules
- Various alert data types: `HostAlertData`, `NidsAlertData`, etc.

## Prerequisites

### System Requirements
- **Java 21** or later
- **Maven 3.8+** for build management
- **Docker** for RabbitMQ container
- **Git** for version control
- **IDE**: IntelliJ IDEA Community Edition recommended

### Development Environment Setup

#### 1. Install Java 21
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk openjdk-21-jdk-headless

# Verify installation
java --version
javac --version
```

#### 2. Install Maven
```bash
# Ubuntu/Debian
sudo apt install maven

# Verify installation
mvn --version
```

#### 3. Install Docker
```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group (optional, logout/login required)
sudo usermod -aG docker $USER
```

#### 4. Setup RabbitMQ Container
```bash
# Start RabbitMQ container (first time)
sudo docker run -d \
  --hostname rabbit-svr \
  --name thesis-rmq \
  -p 8000:15672 \
  -p 5672:5672 \
  -e RABBITMQ_DEFAULT_USER=user \
  -e RABBITMQ_DEFAULT_PASS=password \
  rabbitmq:3-management

# Or restart existing container
docker start thesis-rmq

# Access RabbitMQ Management UI: http://localhost:8000
# Credentials: user/password
```

## Project Dependencies

### Core Dependencies
- **Java 21**: Target runtime and compilation version
- **Gson 2.10.1**: JSON serialization with Java 8 time support
- **RabbitMQ AMQP Client 5.16.0**: Message broker connectivity
- **Maven 3.x**: Build system and dependency management

### Build Plugins
- **maven-compiler-plugin**: Java compilation
- **maven-javadoc-plugin**: API documentation generation
- **maven-site-plugin**: Project site generation
- **maven-exec-plugin**: Application execution

## Quick Start

### 1. Clone and Build
```bash
# Clone the repository
git clone <repository-url>
cd SOARapp

# Build entire project
mvn clean compile

# Package all modules
mvn clean package

# Install to local repository (required for module dependencies)
mvn clean install
```

### 2. Start RabbitMQ
```bash
# Start RabbitMQ container
docker start thesis-rmq
```

### 3. Run the Application
```bash
# Run the main application
cd nis1-thesis-core
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp"
```

## SDK Development Guide

### Creating a New Pluggable Module

#### 1. Add SDK Dependency
Add the SDK dependency to your module's `pom.xml`:
```xml
<dependency>
    <groupId>com.nis1.thesis</groupId>
    <artifactId>nis-thesis-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### 2. Implement PluggableModule Interface
```java
package com.example.mymodule;

import com.nis1.thesis.sdk.*;

/**
 * Example custom security module for the SOAR framework.
 * 
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class MySecurityModule implements PluggableModule {
    private CoreSystemApi api;
    private boolean isRunning = false;
    
    @Override
    public void initialize(CoreSystemApi coreApi) {
        this.api = coreApi;
        
        // Subscribe to relevant events
        api.subscribeToEvent("alerts.host.*", this::handleHostAlert);
        api.subscribeToEvent("alerts.network.*", this::handleNetworkAlert);
        
        System.out.println("MySecurityModule initialized");
        isRunning = true;
    }
    
    @Override
    public String getName() {
        return "MySecurityModule";
    }
    
    @Override
    public void shutdown() {
        isRunning = false;
        System.out.println("MySecurityModule shutting down");
    }
    
    private void handleHostAlert(Event<?> event) {
        // Process host-based security alerts
        System.out.println("Processing host alert: " + event.getType());
        
        // Example: Create mitigation action
        MitigationAction action = new MitigationAction(
            "block_ip", 
            "High risk IP detected", 
            MitigationAction.Severity.HIGH,
            Map.of("ip_address", "192.168.1.100")
        );
        
        Event<MitigationAction> mitigationEvent = Event.of(
            "mitigation.network.block_ip", 
            action
        );
        
        api.publishEvent(mitigationEvent);
    }
    
    private void handleNetworkAlert(Event<?> event) {
        // Process network-based security alerts
        System.out.println("Processing network alert: " + event.getType());
    }
}
```

#### 3. Create Custom Data Types
```java
package com.example.mymodule;

/**
 * Custom alert data for my security module.
 * 
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class MyCustomAlertData {
    private final String alertId;
    private final String source;
    private final String description;
    private final String severity;
    private final Map<String, Object> metadata;
    
    public MyCustomAlertData(String alertId, String source, 
                           String description, String severity, 
                           Map<String, Object> metadata) {
        this.alertId = alertId;
        this.source = source;
        this.description = description;
        this.severity = severity;
        this.metadata = metadata;
    }
    
    // Getters
    public String getAlertId() { return alertId; }
    public String getSource() { return source; }
    public String getDescription() { return description; }
    public String getSeverity() { return severity; }
    public Map<String, Object> getMetadata() { return metadata; }
}
```

### Event Publishing Patterns

#### Publishing Simple Events
```java
// Create event data
HostAlertData alert = new HostAlertData(
    "10.0.0.1", 
    "Suspicious login detected", 
    "HIGH"
);

// Wrap in Event envelope with routing key
Event<HostAlertData> event = Event.of(
    "alerts.host.suspicious_login", 
    alert
);

// Publish through Core API
api.publishEvent(event);
```

#### Event Subscription Patterns
```java
// Exact match subscription
api.subscribeToEvent("alerts.host.malware", this::handleMalwareAlert);

// Wildcard subscriptions
api.subscribeToEvent("alerts.host.*", this::handleAnyHostAlert);
api.subscribeToEvent("alerts.#", this::handleAllAlerts);

// Pattern matching
api.subscribeToEvent("mitigation.*.critical", this::handleCriticalMitigation);
```

### Available SDK Data Types

- **`HostAlertData`**: Host-based security alerts
- **`NidsAlertData`**: Network intrusion detection alerts  
- **`MitigationAction`**: Automated response actions
- **`MitigationCommandData`**: Command execution data
- **`EnrichmentRequestData`**: Data enrichment requests
- **`IpReputationData`**: IP reputation information

### Utility Classes

- **`ModuleHelper`**: Common utilities for module development
- **`Event<T>`**: Generic event wrapper with metadata

## Building and Testing

### Individual Module Testing
```bash
# Compile specific module
mvn -pl nis-thesis-sdk clean compile
mvn -pl nis1-thesis-core clean compile

# Run tests for specific module
mvn -pl nis1-thesis-core test

# Package specific module
mvn -pl nis-thesis-sdk package
```

### Generate Documentation
```bash
# Generate JavaDoc for all modules
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean javadoc:aggregate

# Generate complete project site
mvn site

# View generated documentation
# Open: target/site/apidocs/index.html
```

### Running Individual Components

#### Core System
```bash
cd nis1-thesis-core
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp"
```

#### Test Publisher Module
```bash
cd nis1-thesis-core  
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.TestPublisherModule"
```

## Development Workflow

### 1. Module Development Lifecycle
1. **Design**: Define your module's purpose and event types
2. **Implement**: Create `PluggableModule` implementation
3. **Test**: Write unit tests for your module logic
4. **Integration**: Test with Core system and RabbitMQ
5. **Deploy**: Package and integrate with main application

### 2. Event-Driven Development
1. **Identify Events**: What security events does your module handle?
2. **Define Data Models**: Create payload classes for your events
3. **Implement Handlers**: Write event processing logic
4. **Publish Actions**: Generate response events for other modules

### 3. Best Practices
- **Error Handling**: Always handle `RuntimeException` in event operations
- **Resource Management**: Properly clean up in `shutdown()` method
- **Event Patterns**: Use consistent naming conventions for event types
- **Documentation**: Add comprehensive JavaDoc for all public APIs
- **Testing**: Test both happy path and error scenarios

## Configuration

### RabbitMQ Configuration
- **Host**: `localhost` (default)
- **Management UI**: `http://localhost:8000`
- **AMQP Port**: `5672`
- **Management Port**: `15672`
- **Default Credentials**: `user/password`
- **Exchange**: `security_events` (topic exchange)

### Event Routing
Events are routed using AMQP topic patterns:
- `alerts.host.wazuh` - Wazuh host alerts
- `alerts.network.suricata` - Suricata network alerts  
- `mitigation.firewall.block` - Firewall blocking actions
- `enrichment.ip.reputation` - IP reputation lookups

## Troubleshooting

### Common Issues

1. **RabbitMQ Connection Failed**
   ```bash
   # Check if RabbitMQ is running
   docker ps | grep thesis-rmq
   
   # Restart RabbitMQ if needed
   docker restart thesis-rmq
   ```

2. **Java Version Issues**
   ```bash
   # Set JAVA_HOME explicitly
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   ```

3. **Maven Build Errors**
   ```bash
   # Clean and rebuild
   mvn clean install -U
   ```

4. **Module Not Loading**
   - Check that module implements `PluggableModule` correctly
   - Verify `initialize()` method is called
   - Check console output for error messages

### Debug Mode
```bash
# Run with debug output
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp" -Dexec.args="--debug"
```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-new-feature`
3. Commit changes: `git commit -am 'Add some feature'`
4. Push to branch: `git push origin feature/my-new-feature`
5. Submit a pull request

## License

© 2025 NIS1. All rights reserved.

## Support

For questions and support:
- Check the generated JavaDoc: `target/site/apidocs/index.html`
- Review example modules in `nis1-thesis-core`
- Examine the SDK interfaces in `nis-thesis-sdk`
