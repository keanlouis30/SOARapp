# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Architecture Overview

This is a **multi-module Maven SOAR (Security Orchestration, Automation and Response) framework** built with Java 21. The project implements a **plugin-based event-driven architecture** with RabbitMQ for security event processing and automated response workflows.

### Module Structure
- **`nis-thesis-sdk/`**: Core API interfaces and data models defining the pluggable module contract
- **`nis1-thesis-core/`**: Framework implementation and example modules
- **`middleware-sender/`**: Middleware components for external system integration  
- **`thesis-communication-fabric/`**: Communication utilities and encryption
- **`pythontests/`**: Python-based integration tests
- **Root POM**: Parent Maven configuration coordinating all modules

### Key Design Patterns
- **Plugin Architecture**: Uses `PluggableModule` interface for extensible security modules
- **Event-Driven Architecture**: All communication through typed `Event<T>` objects with routing keys
- **Message Queue Integration**: RabbitMQ topic exchanges for reliable event publishing/subscribing

### Core Components
- `CoreSystemApi`: Interface for modules to publish/subscribe to events
- `Event<T>`: Generic event envelope with metadata and typed payload  
- `PluggableModule`: Contract for all security processing modules
- `ModuleLifecycleManager`: Dynamically loads modules from JAR files
- Event data types: `HostAlertData`, `NidsAlertData`, `MitigationAction`, etc.

## Development Commands

### Project Build & Management
```bash
# Build entire multi-module project
mvn clean compile

# Package all modules 
mvn clean package

# Install to local repository (required for module dependencies)
mvn clean install

# Build specific module only
mvn -pl nis-thesis-sdk clean compile
mvn -pl nis1-thesis-core clean compile
```

### Testing
```bash
# Run tests for specific module
mvn -pl nis1-thesis-core test

# Python integration tests
cd pythontests
pip install pika
python3 test_wazuh_module_communication.py

# Manual module testing (see TESTING_INSTRUCTIONS.md)
javac -cp "nis-thesis-sdk/target/classes:nis1-thesis-core/target/classes" TestModule.java
jar cf modules/test-module.jar -C . com/example/test/TestModule.class
```

### Documentation Generation
```bash
# Generate JavaDoc for all modules
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean javadoc:aggregate

# Generate complete project site
mvn site

# View docs: target/site/apidocs/index.html
```

### Running Applications
```bash
# Start RabbitMQ (required dependency)
docker start thesis-rmq
# Or first time: docker run -d --hostname rabbit-svr --name thesis-rmq -p 8000:15672 -p 5672:5672 -e RABBITMQ_DEFAULT_USER=user -e RABBITMQ_DEFAULT_PASS=password rabbitmq:3-management

# Run main SOAR framework
cd nis1-thesis-core
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.MainApp"

# Run test publisher module
mvn exec:java -Dexec.mainClass="com.nis1.thesis.core.TestPublisherModule"
```

## Plugin Development Workflow

### Creating New Security Modules
1. Add SDK dependency to your module's `pom.xml`
2. Implement `PluggableModule` interface with `initialize()`, `getName()`, `shutdown()` methods
3. Use `CoreSystemApi` to subscribe to events with routing patterns (e.g., `"alerts.host.*"`)
4. Publish response events using `api.publishEvent(Event.of(routingKey, payload))`
5. Package as JAR and place in `modules/` directory for dynamic loading

### Event System
- **Routing Keys**: Use hierarchical patterns like `alerts.host.malware`, `mitigation.firewall.block`
- **Wildcards**: Subscribe with patterns like `alerts.host.*` or `alerts.#`
- **Event Types**: `HostAlertData`, `NidsAlertData`, `MitigationAction`, `IpReputationData`

### External Module Loading
The `ModuleLifecycleManager` scans the `modules/` directory for JAR files containing `PluggableModule` implementations and loads them dynamically at startup.

## Prerequisites

- **Java 21** (compilation and runtime target)
- **Maven 3.8+** for dependency management  
- **Docker** for RabbitMQ container
- **RabbitMQ** running at `localhost:5672` (management UI: `http://localhost:8000`)

## Configuration Notes

- **RabbitMQ Exchange**: `security_events` (topic exchange)
- **Event Routing**: Uses AMQP topic patterns for event delivery
- **Module Discovery**: JAR files in `modules/` directory loaded automatically
- **Dependencies**: Core module depends on SDK; external modules need SDK on classpath
