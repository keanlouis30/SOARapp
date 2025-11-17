# SOAR Framework SDK

**A Thesis-Driven SDK for Pluggable Security Orchestration, Automation, and Response**

## Overview

This Software Development Kit (SDK) provides the foundation for building modular, event-driven security orchestration systems. Designed around a **Policy as Code** philosophy, the SDK enables developers to create pluggable security modules that integrate seamlessly into a distributed SOAR framework for modern ransomware defense.

### Key Features

- **🔌 Plugin Architecture**: Standardized `PluggableModule` interface for easy integration
- **📡 Event-Driven Communication**: High-level API abstracts RabbitMQ/AMQP complexities
- **📦 Type-Safe Data Models**: Strongly-typed event payloads (alerts, commands, threat intelligence)
- **🔄 Flexible Orchestration**: Policy-driven workflows define behavior without code changes
- **🛡️ Production-Ready**: Built for fault-tolerance, resilience, and network transparency

---

## Architecture

The SDK enforces a clean separation between **mechanisms** (pluggable modules) and **policies** (external workflow definitions).

```
┌─────────────────────────────────────────────────────────────┐
│                   Communication Fabric                       │
│              (RabbitMQ Topic Exchange)                       │
└────────────────┬────────────────────────────┬────────────────┘
                 │                            │
     ┌───────────▼────────────┐   ┌───────────▼────────────┐
     │   Collection Modules    │   │   Analysis Modules     │
     │  (EDR, NIDS, Threat     │   │  (Correlation, AI,     │
     │   Intelligence)         │   │   Enrichment)          │
     └───────────┬─────────────┘   └────────────────────────┘
                 │
     ┌───────────▼────────────┐   ┌─────────────────────────┐
     │   Action Modules        │   │  Workflow Engine        │
     │  (SDN Control, Firewall,│   │  (Policy Executor)      │
     │   Incident Management)  │   │                         │
     └─────────────────────────┘   └─────────────────────────┘
```

All modules communicate via **standardized Event objects** published to the Communication Fabric. The SDK provides:

1. **Core Contracts**: `PluggableModule` and `CoreSystemApi` interfaces
2. **Data Models**: `Event<T>`, `HostAlertData`, `NidsAlertData`, `MitigationCommandData`, etc.
3. **Developer Aids**: `ModuleHelper` utility class for common operations

---

## Installation

### Requirements

- **Java 21** (JDK 21 or higher)
- **Maven 3.8+**
- **RabbitMQ** (for runtime, not compilation)

### Adding SDK to Your Project

#### Option 1: Local Maven Install

```bash
# Clone the repository
git clone https://github.com/yourusername/SOARapp.git
cd SOARapp

# Build and install SDK to local Maven repository
mvn clean install
```

Then add to your module's `pom.xml`:

```xml
<dependency>
    <groupId>com.nis1.thesis</groupId>
    <artifactId>nis-thesis-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### Option 2: Direct JAR Dependency

```bash
# Build SDK JAR
cd nis-thesis-sdk
mvn clean package

# Copy JAR to your project's lib/ folder
cp target/nis-thesis-sdk-1.0-SNAPSHOT.jar /path/to/yourproject/lib/
```

---

## Quick Start: Building Your First Module

### Example 1: Simple Threat Intelligence Module

This example demonstrates a basic module that subscribes to host alerts and enriches them with IP reputation data.

```java
package com.example.modules;

import com.nis1.thesis.sdk.*;

public class ThreatIntelModule implements PluggableModule {
    private CoreSystemApi api;
    private ModuleHelper helper;

    @Override
    public String getName() {
        return "ThreatIntel IP Reputation Module";
    }

    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        this.helper = new ModuleHelper(api);
        
        // Subscribe to all host alerts
        api.subscribeToEvent("HOST_ALERT", this::handleHostAlert);
        
        helper.log(getName(), "INFO", "Module initialized and listening for HOST_ALERT events");
    }

    private void handleHostAlert(Event<?> event) {
        HostAlertData alert = (HostAlertData) event.getData();
        String suspiciousIp = alert.getSourceIp();
        
        // Simulate threat intelligence lookup
        boolean isMalicious = queryThreatFeed(suspiciousIp);
        
        // Publish reputation data back to fabric
        helper.publishIpReputation(suspiciousIp, isMalicious, "ThreatIntelX");
        
        helper.log(getName(), "INFO", 
            String.format("Published reputation for %s: %s", suspiciousIp, 
                         isMalicious ? "MALICIOUS" : "CLEAN"));
    }

    private boolean queryThreatFeed(String ip) {
        // TODO: Implement actual API call to ThreatIntelX, VirusTotal, etc.
        return ip.startsWith("192.168."); // Placeholder logic
    }

    @Override
    public void shutdown() {
        helper.log(getName(), "INFO", "Shutting down gracefully");
        // Close connections, persist state, etc.
    }
}
```

### Example 2: Automated Response Module

This example shows an action module that listens for mitigation commands and executes network isolation.

```java
package com.example.modules;

import com.nis1.thesis.sdk.*;

public class NetworkResponseModule implements PluggableModule {
    private CoreSystemApi api;

    @Override
    public String getName() {
        return "SDN Network Response Module";
    }

    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        
        // Subscribe to mitigation commands
        api.subscribeToEvent("INITIATE_MITIGATION", this::handleMitigationCommand);
        
        System.out.println("[" + getName() + "] Ready to execute mitigation actions");
    }

    private void handleMitigationCommand(Event<?> event) {
        MitigationCommandData command = (MitigationCommandData) event.getData();
        
        switch (command.getAction()) {
            case QUARANTINE:
                isolateHost(command.getTargetHost());
                break;
            case BLOCK_IP:
                blockIpAddress(command.getTargetHost());
                break;
            default:
                System.err.println("Unsupported action: " + command.getAction());
        }
        
        System.out.println(String.format("[%s] Executed %s on %s. Reason: %s", 
            getName(), command.getAction(), command.getTargetHost(), 
            command.getJustification()));
    }

    private void isolateHost(String hostIp) {
        // TODO: Call SDN controller API (OpenDaylight, ONOS, etc.)
        System.out.println("→ Sending flow rules to isolate " + hostIp);
    }

    private void blockIpAddress(String ip) {
        // TODO: Update firewall rules via REST API
        System.out.println("→ Installing firewall block rule for " + ip);
    }

    @Override
    public void shutdown() {
        System.out.println("[" + getName() + "] Shutdown complete");
    }
}
```

### Example 3: Advanced Pattern - Correlated Detection Module

This example demonstrates subscribing to multiple event types and performing correlation.

```java
package com.example.modules;

import com.nis1.thesis.sdk.*;
import java.util.*;
import java.time.Instant;

public class CorrelationModule implements PluggableModule {
    private CoreSystemApi api;
    private ModuleHelper helper;
    
    // Track alerts by source IP
    private Map<String, AlertTracker> suspiciousHosts = new HashMap<>();

    @Override
    public String getName() {
        return "Multi-Source Correlation Engine";
    }

    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        this.helper = new ModuleHelper(api);
        
        // Subscribe to multiple alert types using wildcards
        api.subscribeToEvent("HOST_ALERT", this::trackHostAlert);
        api.subscribeToEvent("NIDS_ALERT", this::trackNidsAlert);
        
        helper.log(getName(), "INFO", "Correlation engine active");
    }

    private void trackHostAlert(Event<?> event) {
        HostAlertData alert = (HostAlertData) event.getData();
        incrementThreatScore(alert.getSourceIp(), "HOST_ALERT");
    }

    private void trackNidsAlert(Event<?> event) {
        NidsAlertData alert = (NidsAlertData) event.getData();
        incrementThreatScore(alert.getSourceIp(), "NIDS_ALERT");
    }

    private synchronized void incrementThreatScore(String ip, String source) {
        AlertTracker tracker = suspiciousHosts.computeIfAbsent(
            ip, k -> new AlertTracker(ip)
        );
        
        tracker.addAlert(source);
        
        // If we have corroborating evidence from multiple sources, escalate
        if (tracker.getUniqueSources() >= 2 && tracker.getTotalAlerts() >= 3) {
            helper.log(getName(), "WARN", 
                String.format("Correlated threat detected: %s (%d alerts from %d sources)",
                             ip, tracker.getTotalAlerts(), tracker.getUniqueSources()));
            
            // Publish high-confidence threat event
            helper.publishMitigationCommand(ip, MitigationAction.QUARANTINE,
                "Correlated threat from multiple detection sources");
            
            // Reset tracker after action taken
            suspiciousHosts.remove(ip);
        }
    }

    @Override
    public void shutdown() {
        helper.log(getName(), "INFO", 
            String.format("Shutting down. Tracked %d suspicious hosts", suspiciousHosts.size()));
    }

    // Inner class to track alert patterns
    private static class AlertTracker {
        private final String ip;
        private final Set<String> sources = new HashSet<>();
        private int totalAlerts = 0;

        public AlertTracker(String ip) {
            this.ip = ip;
        }

        public void addAlert(String source) {
            sources.add(source);
            totalAlerts++;
        }

        public int getUniqueSources() {
            return sources.size();
        }

        public int getTotalAlerts() {
            return totalAlerts;
        }
    }
}
```

---

## Core SDK Components

### 1. PluggableModule Interface

The contract all modules must implement. Provides lifecycle hooks:

```java
public interface PluggableModule {
    String getName();                      // Unique module identifier
    void initialize(CoreSystemApi api);    // Called once at startup
    void shutdown();                       // Called during graceful shutdown
}
```

### 2. CoreSystemApi Interface

Your gateway to the Communication Fabric:

```java
public interface CoreSystemApi {
    void publishEvent(Event<?> event);     // Send events to the fabric
    void subscribeToEvent(String eventType, Consumer<Event<?>> listener);
}
```

**Event Type Patterns** (AMQP Topic Syntax):
- `"HOST_ALERT"` - Exact match
- `"alerts.host.*"` - Single-word wildcard (matches `alerts.host.wazuh`, `alerts.host.crowdstrike`)
- `"alerts.#"` - Multi-word wildcard (matches `alerts.host.wazuh`, `alerts.network.suricata`)
- `"#"` - All events (use sparingly!)

### 3. Event<T> Class

Generic envelope for all inter-module communication:

```java
Event<HostAlertData> event = Event.of("HOST_ALERT_WAZUH", alertPayload);
api.publishEvent(event);
```

Auto-generated fields:
- `id` (UUID)
- `timestamp` (Instant)
- `type` (routing key)
- `data` (your payload)

### 4. Standard Data Payloads

| Class | Purpose | Key Fields |
|-------|---------|------------|
| `HostAlertData` | Host-based security alerts | `sourceIp`, `description`, `severity` |
| `NidsAlertData` | Network intrusion alerts | `sourceIp`, `destinationIp`, `signature`, `signatureSeverity` |
| `MitigationCommandData` | Automated response actions | `targetHost`, `action`, `justification` |
| `IpReputationData` | Threat intelligence | `ipAddress`, `isMalicious`, `source`, `confidenceScore` |
| `EnrichmentRequestData` | Threat intel requests | `ipAddress`, `domain`, `fileHash`, `enrichmentType` |

### 5. MitigationAction Enum

Standardized response actions:

```java
public enum MitigationAction {
    QUARANTINE,        // Isolate host to quarantine VLAN
    BLOCK_IP,          // Block traffic at firewall/network level
    RATE_LIMIT,        // Apply traffic throttling
    REDIRECT_TRAFFIC,  // Send to honeypot/analysis system
    ISOLATE_VLAN,      // Move to isolated VLAN
    ALERT_ONLY,        // Generate alerts without automated action
    DISABLE_USER,      // Disable compromised account
    KILL_PROCESS       // Terminate malicious process
}
```

---

## Building and Packaging

### Compile Your Module

```bash
mvn clean compile
```

### Package as JAR

```bash
mvn clean package
```

Your module JAR will be in `target/your-module-1.0-SNAPSHOT.jar`.

### Deploy to SOAR Framework

Copy your JAR to the framework's `modules/` directory. The ModuleLifecycleManager will automatically:

1. Scan the JAR for classes implementing `PluggableModule`
2. Instantiate your module
3. Call `initialize(api)` with a `CoreSystemApi` handle
4. Begin event delivery

No configuration files required—just drop the JAR and restart the framework.

---

## Testing Your Module

### Unit Testing with Mock API

```java
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

public class ThreatIntelModuleTest {
    @Test
    public void testModuleSubscribesToCorrectEvents() {
        // Create mock API
        CoreSystemApi mockApi = mock(CoreSystemApi.class);
        
        // Initialize module
        ThreatIntelModule module = new ThreatIntelModule();
        module.initialize(mockApi);
        
        // Verify subscription was registered
        verify(mockApi).subscribeToEvent(eq("HOST_ALERT"), any());
    }
}
```

### Integration Testing

The SDK includes support for full integration tests with RabbitMQ. See `pythontests/` directory for examples using the Python `pika` library to simulate event publishing.

---

## Policy-Driven Workflows

While modules provide **mechanisms** (detection, response capabilities), workflows define **policies** (when and how to act).

### Example Workflow (YAML)

```yaml
name: "High-Confidence Ransomware Response"
version: 1.0
description: >
  Automatically quarantine hosts when both EDR and NIDS detect ransomware activity.

trigger:
  event_type: "HOST_ALERT"
  condition: "{{ trigger.data.severity == 'CRITICAL' }}"

steps:
  - name: "Request Network Traffic Analysis"
    action:
      type: "PUBLISH_EVENT"
      event:
        type: "INITIATE_TRAFFIC_ANALYSIS"
        data:
          targetHost: "{{ trigger.data.sourceIp }}"

  - name: "Wait for NIDS Confirmation"
    wait_for:
      event_type: "NIDS_ALERT"
      timeout: "5 minutes"
      conditions:
        - "{{ new_event.data.sourceIp == trigger.data.sourceIp }}"
        - "{{ new_event.data.signatureSeverity == '1' }}"

  - name: "Initiate Automatic Quarantine"
    action:
      type: "PUBLISH_EVENT"
      event:
        type: "INITIATE_MITIGATION"
        data:
          targetHost: "{{ trigger.data.sourceIp }}"
          action: "QUARANTINE"
          justification: "Correlated ransomware indicators from EDR + NIDS"
```

This workflow uses events published by **your modules** to make automated decisions without changing any code.

---

## Design Philosophy & Principles

### 1. **Policy as Code**
Security logic is externalized in auditable YAML workflows, not hard-coded. Change your response strategy by editing a file, not recompiling modules.

### 2. **Fail-Safe by Default**
- Modules that crash during initialization are isolated (won't crash the framework)
- Network outages trigger automatic reconnection with exponential backoff
- Unknown events are logged and displayed to operators, never silently dropped

### 3. **Network Transparency**
All events are serialized as **human-readable JSON**. Use Wireshark to inspect AMQP traffic and see exactly what your modules are publishing.

### 4. **Decoupled Integration**
Modules never call each other directly. A NIDS module doesn't need to know if a firewall module exists—it just publishes alerts and trusts the fabric to route them.

---

## Advanced Topics

### Custom Event Types

You're not limited to SDK-provided payloads. Define your own:

```java
public class DatabaseAnomalyData {
    private String database;
    private String query;
    private int rowsAffected;
    // ... constructors, getters, setters
}

// Publish custom event
Event<DatabaseAnomalyData> event = Event.of(
    "alerts.database.anomaly", 
    new DatabaseAnomalyData("prod-db-01", "DELETE FROM users", 500000)
);
api.publishEvent(event);
```

### Thread Safety

The `CoreSystemApi` is thread-safe. You can call `publishEvent()` from multiple threads. However, your event **listeners** are called on a single thread per subscription, so you don't need synchronization for reads—but use `synchronized` when modifying shared module state.

### Resource Management

Always clean up in `shutdown()`:

```java
@Override
public void shutdown() {
    if (httpClient != null) {
        httpClient.close();
    }
    if (databaseConnection != null) {
        databaseConnection.close();
    }
}
```

---

## SDK Limitations & Boundaries

The SDK is **not suitable** for:

- ❌ **In-line packet processing** (e.g., implementing an IPS that blocks packets in real-time)
- ❌ **Synchronous request/response** patterns (e.g., file access authorization that blocks until a decision)
- ❌ **Low-level SDN control plane** logic (use the Northbound API, not Southbound)

The SDK is **ideal** for:

- ✅ Out-of-band security analysis and orchestration
- ✅ Integrating external security tools (EDR, SIEM, threat intel, firewalls)
- ✅ Policy-driven automated response workflows
- ✅ Threat correlation and enrichment pipelines

---

## Documentation & Support

### Generated JavaDoc

```bash
# Generate API documentation
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean javadoc:aggregate

# Open in browser
firefox target/site/apidocs/index.html
```

### Project Structure

```
SOARapp/
├── nis-thesis-sdk/              # 👈 The SDK (this package)
│   ├── src/main/java/com/nis1/thesis/sdk/
│   │   ├── PluggableModule.java
│   │   ├── CoreSystemApi.java
│   │   ├── Event.java
│   │   ├── HostAlertData.java
│   │   ├── NidsAlertData.java
│   │   ├── MitigationAction.java
│   │   ├── MitigationCommandData.java
│   │   ├── IpReputationData.java
│   │   ├── EnrichmentRequestData.java
│   │   └── ModuleHelper.java
│   └── pom.xml
├── SDK Detailed context.pdf     # Thesis design documentation
├── README.md                    # This file
└── pom.xml                      # Parent POM
```

---

## License

This SDK is developed as part of a master's thesis on security orchestration for ransomware defense.

---

## Citation

If you use this SDK in academic work, please cite:

```
@mastersthesis{nis1thesis2025,
  author  = {[Your Name]},
  title   = {Security Orchestration, Automation and Response Framework for Ransomware Defense},
  school  = {[Your University]},
  year    = {2025},
  type    = {Master's Thesis}
}
```

---

## Contact

For questions or collaboration:
- **GitHub Issues**: [github.com/yourusername/SOARapp/issues](https://github.com/yourusername/SOARapp/issues)
- **Email**: your.email@university.edu

---

**Built with ❤️ for the security community**
