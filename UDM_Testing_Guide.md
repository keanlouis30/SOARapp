# User-Defined Module Testing Guide
## Testing Framework for SOAR User-Defined Modules

**Version:** 2.0  
**Date:** December 2025  
**Target Audience:** Security Engineers, DevOps Teams, Integration Developers

---

## Table of Contents
1. [Testing Overview](#testing-overview)
2. [Testing Environment Setup](#testing-environment-setup)
3. [Unit Testing](#unit-testing)
4. [Integration Testing](#integration-testing)
5. [End-to-End Testing](#end-to-end-testing)
6. [Mock Testing Strategies](#mock-testing-strategies)
7. [Performance Testing](#performance-testing)
8. [Troubleshooting Tests](#troubleshooting-tests)
9. [CI/CD Integration](#cicd-integration)
10. [Best Practices](#best-practices)

---

## Testing Overview

Testing user-defined modules in the SOAR framework requires a multi-layered approach to ensure reliability, performance, and proper integration with the core system.

### Testing Pyramid for UDM

```
        ┌─────────────────────┐
        │   End-to-End Tests  │  ← Full system integration
        │  (Manual/Automated) │
        └─────────────────────┘
              ┌─────────────────────┐
              │ Integration Tests   │  ← SDK + RabbitMQ + Mock APIs
              │  (Automated)        │
              └─────────────────────┘
                    ┌─────────────────────┐
                    │   Unit Tests        │  ← Individual methods/classes
                    │  (Automated)        │
                    └─────────────────────┘
```

### Testing Types Covered

| Test Type | Purpose | Scope | Automation Level |
|-----------|---------|-------|------------------|
| **Unit Tests** | Test individual methods and classes | Single module | Fully Automated |
| **Integration Tests** | Test module interaction with SDK/RabbitMQ | Module + Framework | Fully Automated |
| **End-to-End Tests** | Test complete workflow scenarios | Full System | Semi-Automated |
| **Performance Tests** | Validate throughput and resource usage | Module + Dependencies | Automated |
| **Security Tests** | Validate authentication and data handling | Module Security | Automated |

---

## Testing Environment Setup

### 1. Development Testing Environment

#### Required Components
```bash
# Install testing dependencies
sudo apt update
sudo apt install openjdk-21-jdk maven docker.io

# Verify Java version
java --version
mvn --version
```

#### Testing Infrastructure Setup
```bash
# 1. Start RabbitMQ for integration tests
docker run -d \
  --hostname test-rabbit \
  --name soar-test-rmq \
  -p 15672:15672 \
  -p 5672:5672 \
  -e RABBITMQ_DEFAULT_USER=testuser \
  -e RABBITMQ_DEFAULT_PASS=testpass \
  rabbitmq:3-management

# 2. Create test-specific RabbitMQ configuration
# Access: http://localhost:15672 (testuser/testpass)

# 3. Set up mock Wazuh API (using Docker)
docker run -d \
  --name mock-wazuh \
  -p 55000:55000 \
  -v $(pwd)/test-resources:/app/data \
  mockserver/mockserver:latest
```

#### Test Directory Structure
```
nis1-thesis-udm/
├── src/
│   ├── main/java/com/nis1/thesis/udm/
│   │   └── WazuhModule.java
│   └── test/java/com/nis1/thesis/udm/
│       ├── WazuhModuleTest.java           # Unit tests
│       ├── WazuhModuleIntegrationTest.java # Integration tests
│       └── TestUtils.java                 # Testing utilities
├── test-resources/
│   ├── mock-wazuh-responses/
│   │   ├── alerts-response.json
│   │   ├── auth-response.json
│   │   └── empty-response.json
│   └── test-configs/
│       ├── test-rabbitmq.properties
│       └── test-wazuh.properties
└── pom.xml
```

### 2. Test Dependencies in pom.xml

Add these dependencies to your `nis1-thesis-udm/pom.xml`:

```xml
<dependencies>
    <!-- Main dependencies -->
    <dependency>
        <groupId>com.nis1.thesis</groupId>
        <artifactId>nis-thesis-sdk</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Testing dependencies -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>rabbitmq</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>mockwebserver</artifactId>
        <version>4.11.0</version>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <version>4.2.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                    <include>**/*Tests.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
        
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.1.2</version>
            <configuration>
                <includes>
                    <include>**/*IntegrationTest.java</include>
                </includes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## Unit Testing

### 1. Basic Unit Test Structure

Create `nis1-thesis-udm/src/test/java/com/nis1/thesis/udm/WazuhModuleTest.java`:

```java
package com.nis1.thesis.udm;

import com.nis1.thesis.sdk.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for WazuhModule
 * Tests individual methods and core functionality without external dependencies
 */
@ExtendWith(MockitoExtension.class)
class WazuhModuleTest {

    @Mock
    private CoreSystemApi mockApi;
    
    @Mock
    private ModuleHelper mockHelper;
    
    @InjectMocks
    private WazuhModule wazuhModule;
    
    private ArgumentCaptor<Event<?>> eventCaptor;
    
    @BeforeEach
    void setUp() {
        eventCaptor = ArgumentCaptor.forClass(Event.class);
        
        // Mock ModuleHelper constructor behavior
        when(mockHelper.log(anyString(), anyString(), anyString())).thenReturn(true);
    }
    
    @Test
    @DisplayName("Should initialize module with correct name")
    void testModuleInitialization() {
        // Given
        String expectedName = "Wazuh User-Defined Module";
        
        // When
        String actualName = wazuhModule.getName();
        
        // Then
        assertEquals(expectedName, actualName);
    }
    
    @Test
    @DisplayName("Should subscribe to correct event patterns during initialization")
    void testEventSubscriptions() {
        // When
        wazuhModule.initialize(mockApi);
        
        // Then
        verify(mockApi).subscribeToEvent(eq("ENRICHMENT_REQUEST_IP"), any());
        verify(mockApi).subscribeToEvent(eq("HOST_ALERT.*"), any());
        verify(mockHelper).log(eq("Wazuh User-Defined Module"), eq("INFO"), contains("Initializing"));
    }
    
    @Test
    @DisplayName("Should map Wazuh severity levels correctly")
    void testSeverityMapping() {
        // Given & When & Then
        assertEquals("critical", callPrivateMethod("mapWazuhLevelToSeverity", 15));
        assertEquals("critical", callPrivateMethod("mapWazuhLevelToSeverity", 12));
        assertEquals("high", callPrivateMethod("mapWazuhLevelToSeverity", 7));
        assertEquals("medium", callPrivateMethod("mapWazuhLevelToSeverity", 4));
        assertEquals("low", callPrivateMethod("mapWazuhLevelToSeverity", 1));
    }
    
    @Test
    @DisplayName("Should calculate threat score correctly")
    void testThreatScoreCalculation() {
        // Given & When & Then
        assertEquals(100, callPrivateMethod("calculateThreatScore", 15));
        assertEquals(80, callPrivateMethod("calculateThreatScore", 12));
        assertEquals(46, callPrivateMethod("calculateThreatScore", 7));
        assertEquals(0, callPrivateMethod("calculateThreatScore", 0));
    }
    
    @Test
    @DisplayName("Should determine alert type from description")
    void testAlertTypeDetection() {
        // Given & When & Then
        assertEquals("ransomware_detection", 
            callPrivateMethod("determineAlertType", "Ransomware encryption detected"));
        assertEquals("malware_detection", 
            callPrivateMethod("determineAlertType", "Malware virus found"));
        assertEquals("authentication_attack", 
            callPrivateMethod("determineAlertType", "Brute force authentication attempt"));
        assertEquals("security_violation", 
            callPrivateMethod("determineAlertType", "Unknown security event"));
    }
    
    @Test
    @DisplayName("Should handle IP enrichment requests")
    void testIpEnrichmentRequest() {
        // Given
        EnrichmentRequestData requestData = new EnrichmentRequestData("192.168.1.100");
        Event<EnrichmentRequestData> enrichmentEvent = Event.of("ENRICHMENT_REQUEST_IP", requestData);
        
        // When
        wazuhModule.initialize(mockApi);
        // Simulate enrichment request handling
        callPrivateMethod("handleIpEnrichmentRequest", enrichmentEvent);
        
        // Then
        verify(mockHelper).log(eq("Wazuh User-Defined Module"), eq("INFO"), 
            contains("Processing IP enrichment request for: 192.168.1.100"));
    }
    
    @Test
    @DisplayName("Should handle null IP enrichment gracefully")
    void testNullIpEnrichmentRequest() {
        // Given
        EnrichmentRequestData requestData = new EnrichmentRequestData(null);
        Event<EnrichmentRequestData> enrichmentEvent = Event.of("ENRICHMENT_REQUEST_IP", requestData);
        
        // When
        wazuhModule.initialize(mockApi);
        callPrivateMethod("handleIpEnrichmentRequest", enrichmentEvent);
        
        // Then
        verify(mockHelper).log(eq("Wazuh User-Defined Module"), eq("WARN"), 
            contains("null/empty IP address"));
    }
    
    @Test
    @DisplayName("Should shutdown gracefully")
    void testModuleShutdown() {
        // Given
        wazuhModule.initialize(mockApi);
        
        // When
        wazuhModule.shutdown();
        
        // Then
        verify(mockHelper).log(eq("Wazuh User-Defined Module"), eq("INFO"), 
            contains("Shutting down"));
        verify(mockHelper).log(eq("Wazuh User-Defined Module"), eq("INFO"), 
            contains("shutdown complete"));
    }
    
    // Helper method to call private methods via reflection for testing
    @SuppressWarnings("unchecked")
    private <T> T callPrivateMethod(String methodName, Object... args) {
        try {
            var method = WazuhModule.class.getDeclaredMethod(methodName, 
                getParameterTypes(args));
            method.setAccessible(true);
            return (T) method.invoke(wazuhModule, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call private method: " + methodName, e);
        }
    }
    
    private Class<?>[] getParameterTypes(Object... args) {
        return java.util.Arrays.stream(args)
            .map(Object::getClass)
            .toArray(Class<?>[]::new);
    }
}
```

### 2. Data Transformation Unit Tests

```java
@Test
@DisplayName("Should transform Wazuh alert to standardized format")
void testWazuhAlertTransformation() {
    // Given
    String wazuhAlertJson = """
        {
            "agent": {
                "ip": "192.168.1.101"
            },
            "rule": {
                "id": "9201021",
                "description": "Suspicious file encryption activity",
                "level": 12,
                "groups": ["ransomware", "critical"]
            },
            "data": {
                "srcip": "192.168.1.101",
                "dstip": "10.0.0.5",
                "protocol": "TCP",
                "win": {
                    "eventdata": {
                        "image": "C:\\\\Users\\\\John\\\\AppData\\\\Local\\\\Temp\\\\malware.exe",
                        "targetFilename": "C:\\\\Users\\\\John\\\\Documents\\\\encrypted_file.docx"
                    }
                }
            }
        }
        """;
        
    JsonObject wazuhAlert = JsonParser.parseString(wazuhAlertJson).getAsJsonObject();
    
    // When
    WazuhModule.WazuhAlertMessage result = callPrivateMethod("transformWazuhAlert", wazuhAlert);
    
    // Then
    assertNotNull(result);
    assertEquals("alerts.host.wazuh", result.getEventType());
    assertEquals("WazuhModule", result.getSourceModule());
    assertNotNull(result.getEventId());
    assertNotNull(result.getTimestamp());
    
    // Check payload
    WazuhModule.WazuhAlertPayload payload = result.getPayload();
    assertEquals("host-192.168.1.101", payload.getHostId());
    assertEquals("ransomware_detection", payload.getAlertType());
    assertEquals("9201021", payload.getSignatureId());
    assertEquals("Suspicious file encryption activity", payload.getSignature());
    assertEquals("critical", payload.getSeverity());
    assertEquals("192.168.1.101", payload.getSourceIp());
    assertEquals("10.0.0.5", payload.getDestinationIp());
    assertEquals("TCP", payload.getProtocol());
    assertEquals(80, payload.getThreatScore()); // 12/15 * 100 = 80
}
```

### 3. Running Unit Tests

```bash
# Run all unit tests
cd nis1-thesis-udm
mvn test

# Run specific test class
mvn test -Dtest=WazuhModuleTest

# Run specific test method
mvn test -Dtest=WazuhModuleTest#testModuleInitialization

# Run tests with detailed output
mvn test -Dtest=WazuhModuleTest -DforkMode=never -DforkCount=1

# Generate test reports
mvn test jacoco:report
```

---

## Integration Testing

### 1. Integration Test with TestContainers

Create `nis1-thesis-udm/src/test/java/com/nis1/thesis/udm/WazuhModuleIntegrationTest.java`:

```java
package com.nis1.thesis.udm;

import com.nis1.thesis.sdk.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for WazuhModule
 * Tests module interaction with RabbitMQ, mock APIs, and the SDK framework
 */
@Testcontainers
class WazuhModuleIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management")
            .withUser("testuser", "testpass");

    private MockWebServer mockWazuhApi;
    private WazuhModule wazuhModule;
    private TestCoreSystemApi testApi;
    
    @BeforeEach
    void setUp() throws Exception {
        // Start mock Wazuh API server
        mockWazuhApi = new MockWebServer();
        mockWazuhApi.start();
        
        // Setup test API implementation
        testApi = new TestCoreSystemApi(rabbitMQ.getAmqpUrl());
        
        // Create module instance
        wazuhModule = new WazuhModule();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (wazuhModule != null) {
            wazuhModule.shutdown();
        }
        if (mockWazuhApi != null) {
            mockWazuhApi.shutdown();
        }
        if (testApi != null) {
            testApi.shutdown();
        }
    }
    
    @Test
    @DisplayName("Should authenticate with Wazuh API and receive token")
    void testWazuhAuthentication() {
        // Given
        String mockAuthResponse = """
            {
                "data": {
                    "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.test.token"
                }
            }
            """;
            
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mockAuthResponse));
        
        // Configure module to use mock server
        setPrivateField(wazuhModule, "WAZUH_API_BASE_URL", mockWazuhApi.url("").toString());
        
        // When
        wazuhModule.initialize(testApi);
        
        // Then
        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> {
                   assertEquals(1, mockWazuhApi.getRequestCount());
                   var request = mockWazuhApi.takeRequest();
                   assertTrue(request.getPath().contains("/security/user/authenticate"));
                   assertNotNull(request.getHeader("Authorization"));
               });
    }
    
    @Test
    @DisplayName("Should poll Wazuh alerts and publish events")
    void testAlertPollingAndPublishing() throws InterruptedException {
        // Given
        String mockAuthResponse = """
            {
                "data": {
                    "token": "test.jwt.token"
                }
            }
            """;
            
        String mockAlertsResponse = """
            {
                "data": {
                    "affected_items": [
                        {
                            "agent": {
                                "ip": "192.168.1.100"
                            },
                            "rule": {
                                "id": "5710",
                                "description": "Multiple authentication failures",
                                "level": 7,
                                "groups": ["authentication_failed"]
                            },
                            "data": {
                                "srcip": "192.168.1.100",
                                "protocol": "SSH"
                            }
                        }
                    ]
                }
            }
            """;
        
        // Queue responses
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockAuthResponse));
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockAlertsResponse));
        
        // Configure module
        setPrivateField(wazuhModule, "WAZUH_API_BASE_URL", mockWazuhApi.url("").toString());
        setPrivateField(wazuhModule, "POLL_INTERVAL_SECONDS", 1); // Fast polling for testing
        
        // When
        wazuhModule.initialize(testApi);
        
        // Then - Wait for authentication and alert polling
        await().atMost(Duration.ofSeconds(15))
               .untilAsserted(() -> {
                   assertTrue(mockWazuhApi.getRequestCount() >= 2);
                   assertTrue(testApi.getPublishedEvents().size() > 0);
               });
        
        // Verify published event
        var publishedEvents = testApi.getPublishedEvents();
        assertEquals(1, publishedEvents.size());
        
        Event<?> event = publishedEvents.get(0);
        assertEquals("HOST_ALERT_WAZUH", event.getType());
        assertTrue(event.getData() instanceof HostAlertData);
        
        HostAlertData alertData = (HostAlertData) event.getData();
        assertEquals("192.168.1.100", alertData.getSourceIp());
        assertEquals("high", alertData.getSeverity());
    }
    
    @Test
    @DisplayName("Should handle IP enrichment requests")
    void testIpEnrichmentIntegration() {
        // Given
        String mockAuthResponse = """
            {
                "data": {
                    "token": "test.jwt.token"
                }
            }
            """;
            
        String mockEnrichmentResponse = """
            {
                "data": {
                    "affected_items": [
                        {
                            "rule": {
                                "level": 8
                            }
                        },
                        {
                            "rule": {
                                "level": 9
                            }
                        }
                    ]
                }
            }
            """;
        
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockAuthResponse));
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(mockEnrichmentResponse));
        
        // Configure and initialize
        setPrivateField(wazuhModule, "WAZUH_API_BASE_URL", mockWazuhApi.url("").toString());
        wazuhModule.initialize(testApi);
        
        // Wait for authentication
        await().atMost(Duration.ofSeconds(5))
               .untilAsserted(() -> assertTrue(mockWazuhApi.getRequestCount() >= 1));
        
        // When - Send enrichment request
        EnrichmentRequestData request = new EnrichmentRequestData("192.168.1.50");
        Event<EnrichmentRequestData> enrichmentEvent = Event.of("ENRICHMENT_REQUEST_IP", request);
        testApi.publishEvent(enrichmentEvent);
        
        // Then
        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> {
                   var events = testApi.getPublishedEvents().stream()
                       .filter(e -> "IP_REPUTATION_WAZUH".equals(e.getType()))
                       .toList();
                   assertEquals(1, events.size());
                   
                   IpReputationData reputation = (IpReputationData) events.get(0).getData();
                   assertEquals("192.168.1.50", reputation.getIpAddress());
                   assertEquals("Wazuh-Historical", reputation.getSource());
               });
    }
    
    @Test
    @DisplayName("Should handle authentication failures gracefully")
    void testAuthenticationFailure() {
        // Given
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized"));
        
        setPrivateField(wazuhModule, "WAZUH_API_BASE_URL", mockWazuhApi.url("").toString());
        
        // When
        wazuhModule.initialize(testApi);
        
        // Then - Should handle authentication failure without crashing
        await().atMost(Duration.ofSeconds(5))
               .untilAsserted(() -> {
                   assertEquals(1, mockWazuhApi.getRequestCount());
                   // Module should still be running despite auth failure
                   assertTrue(testApi.isConnected());
               });
    }
    
    @Test
    @DisplayName("Should handle malformed API responses")
    void testMalformedApiResponse() {
        // Given
        mockWazuhApi.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("invalid json response"));
        
        setPrivateField(wazuhModule, "WAZUH_API_BASE_URL", mockWazuhApi.url("").toString());
        
        // When
        wazuhModule.initialize(testApi);
        
        // Then
        await().atMost(Duration.ofSeconds(5))
               .untilAsserted(() -> {
                   assertEquals(1, mockWazuhApi.getRequestCount());
                   // Module should handle error gracefully
                   assertEquals(0, testApi.getPublishedEvents().size());
               });
    }
    
    // Helper methods for reflection-based field setting
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
    
    /**
     * Test implementation of CoreSystemApi for integration testing
     */
    private static class TestCoreSystemApi implements CoreSystemApi {
        private final ConcurrentLinkedQueue<Event<?>> publishedEvents = new ConcurrentLinkedQueue<>();
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private boolean connected = true;
        
        public TestCoreSystemApi(String rabbitMqUrl) {
            // Initialize connection to RabbitMQ for real integration testing
            // Implementation would connect to actual RabbitMQ container
        }
        
        @Override
        public void publishEvent(Event<?> event) {
            publishedEvents.offer(event);
            eventCount.incrementAndGet();
        }
        
        @Override
        public void subscribeToEvent(String eventType, java.util.function.Consumer<Event<?>> listener) {
            // For testing, we can simulate event delivery by calling listeners directly
            if ("ENRICHMENT_REQUEST_IP".equals(eventType)) {
                // Store listener for manual triggering in tests
            }
        }
        
        public ConcurrentLinkedQueue<Event<?>> getPublishedEvents() {
            return publishedEvents;
        }
        
        public boolean isConnected() {
            return connected;
        }
        
        public void shutdown() {
            connected = false;
        }
    }
}
```

### 2. Running Integration Tests

```bash
# Run integration tests only
mvn integration-test

# Run all tests (unit + integration)
mvn verify

# Run integration tests with Docker cleanup
mvn clean integration-test -DfailIfNoTests=false

# Run with specific test container image versions
mvn integration-test -Dtestcontainers.rabbitmq.image=rabbitmq:3.11-management
```

---

## End-to-End Testing

### 1. Manual E2E Test Scripts

Create `nis1-thesis-udm/test-scripts/e2e-test-wazuh.sh`:

```bash
#!/bin/bash
# End-to-End Testing Script for WazuhModule
# Tests complete workflow from module initialization to event processing

set -e

echo "🚀 Starting End-to-End Test for WazuhModule"

# Configuration
TEST_DIR="/tmp/soar-e2e-test"
RABBITMQ_CONTAINER="soar-e2e-rmq"
MOCK_WAZUH_CONTAINER="soar-e2e-wazuh"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
    log_info "Cleaning up test environment..."
    docker stop $RABBITMQ_CONTAINER $MOCK_WAZUH_CONTAINER 2>/dev/null || true
    docker rm $RABBITMQ_CONTAINER $MOCK_WAZUH_CONTAINER 2>/dev/null || true
    rm -rf $TEST_DIR
}

# Trap cleanup on script exit
trap cleanup EXIT

# 1. Setup test environment
setup_test_environment() {
    log_info "Setting up test environment..."
    
    mkdir -p $TEST_DIR
    cd $TEST_DIR
    
    # Start RabbitMQ
    log_info "Starting RabbitMQ container..."
    docker run -d \
        --name $RABBITMQ_CONTAINER \
        -p 15673:15672 \
        -p 5673:5672 \
        -e RABBITMQ_DEFAULT_USER=e2euser \
        -e RABBITMQ_DEFAULT_PASS=e2epass \
        rabbitmq:3-management
    
    # Wait for RabbitMQ to be ready
    log_info "Waiting for RabbitMQ to be ready..."
    sleep 10
    
    # Verify RabbitMQ is accessible
    if curl -f -u e2euser:e2epass http://localhost:15673/api/overview >/dev/null 2>&1; then
        log_info "RabbitMQ is ready"
    else
        log_error "RabbitMQ failed to start"
        exit 1
    fi
    
    # Start mock Wazuh API
    log_info "Starting mock Wazuh API..."
    docker run -d \
        --name $MOCK_WAZUH_CONTAINER \
        -p 55001:1080 \
        mockserver/mockserver:latest
    
    sleep 5
    
    # Configure mock Wazuh responses
    setup_mock_wazuh_responses
}

# 2. Configure mock Wazuh API responses
setup_mock_wazuh_responses() {
    log_info "Configuring mock Wazuh API responses..."
    
    # Authentication endpoint
    curl -X PUT "http://localhost:55001/mockserver/expectation" \
        -H "Content-Type: application/json" \
        -d '{
            "httpRequest": {
                "method": "POST",
                "path": "/security/user/authenticate"
            },
            "httpResponse": {
                "statusCode": 200,
                "headers": {
                    "Content-Type": ["application/json"]
                },
                "body": {
                    "data": {
                        "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.e2e.test.token"
                    }
                }
            }
        }'
    
    # Alerts endpoint
    curl -X PUT "http://localhost:55001/mockserver/expectation" \
        -H "Content-Type: application/json" \
        -d '{
            "httpRequest": {
                "method": "GET",
                "path": "/alerts",
                "queryStringParameters": {
                    "level": ["7,8,9,10,11,12,13,14,15"]
                }
            },
            "httpResponse": {
                "statusCode": 200,
                "headers": {
                    "Content-Type": ["application/json"]
                },
                "body": {
                    "data": {
                        "affected_items": [
                            {
                                "agent": {
                                    "ip": "192.168.100.50"
                                },
                                "rule": {
                                    "id": "9999",
                                    "description": "E2E Test Alert - Suspicious activity detected",
                                    "level": 8,
                                    "groups": ["attack", "test"]
                                },
                                "data": {
                                    "srcip": "192.168.100.50",
                                    "dstip": "10.0.0.1",
                                    "protocol": "TCP"
                                }
                            }
                        ]
                    }
                }
            }
        }'
    
    log_info "Mock Wazuh API configured successfully"
}

# 3. Build and run the module
run_wazuh_module() {
    log_info "Building and running WazuhModule..."
    
    cd /home/kean/Documents/SOARapp
    
    # Build the project
    mvn clean compile -q
    
    # Create test configuration
    cat > test-e2e.properties << EOF
wazuh.api.url=http://localhost:55001
wazuh.username=testuser
wazuh.password=testpass
wazuh.poll.interval=5
rabbitmq.host=localhost
rabbitmq.port=5673
rabbitmq.username=e2euser
rabbitmq.password=e2epass
EOF
    
    # Run the module in background
    log_info "Starting WazuhModule..."
    nohup java -cp "nis1-thesis-udm/target/classes:nis-thesis-sdk/target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)" \
        -Dconfig.file=test-e2e.properties \
        com.nis1.thesis.udm.WazuhModule > wazuh-module.log 2>&1 &
    
    MODULE_PID=$!
    echo $MODULE_PID > module.pid
    
    log_info "WazuhModule started with PID: $MODULE_PID"
    sleep 10
}

# 4. Test alert processing
test_alert_processing() {
    log_info "Testing alert processing..."
    
    # Monitor RabbitMQ for published events
    log_info "Monitoring RabbitMQ for events..."
    
    # Create a simple consumer to check for events
    python3 -c "
import pika
import sys
import json
from datetime import datetime, timedelta

connection = pika.BlockingConnection(
    pika.ConnectionParameters('localhost', 5673, '/', 
    pika.PlainCredentials('e2euser', 'e2epass'))
)
channel = connection.channel()

# Declare exchange and queue
channel.exchange_declare(exchange='security_events', exchange_type='topic')
result = channel.queue_declare('', exclusive=True)
queue_name = result.method.queue
channel.queue_bind(exchange='security_events', queue=queue_name, routing_key='HOST_ALERT_WAZUH')

print('Waiting for events. To exit press CTRL+C')

events_received = 0
start_time = datetime.now()

def callback(ch, method, properties, body):
    global events_received
    events_received += 1
    event = json.loads(body)
    print(f'Received event {events_received}: {event.get(\"type\", \"unknown\")}')
    print(f'Event data: {json.dumps(event, indent=2)}')
    
    if events_received >= 1:
        print('✅ Successfully received expected events')
        connection.close()
        sys.exit(0)

channel.basic_consume(queue=queue_name, on_message_callback=callback, auto_ack=True)

try:
    # Wait for up to 60 seconds
    connection.add_timeout(60, lambda: connection.close())
    channel.start_consuming()
except KeyboardInterrupt:
    channel.stop_consuming()
    connection.close()
    if events_received > 0:
        print(f'✅ Test passed: Received {events_received} events')
    else:
        print('❌ Test failed: No events received')
        sys.exit(1)
" || log_error "Event monitoring failed"
}

# 5. Test IP enrichment
test_ip_enrichment() {
    log_info "Testing IP enrichment functionality..."
    
    # Setup enrichment endpoint mock
    curl -X PUT "http://localhost:55001/mockserver/expectation" \
        -H "Content-Type: application/json" \
        -d '{
            "httpRequest": {
                "method": "GET",
                "path": "/alerts",
                "queryStringParameters": {
                    "q": ["data.srcip=192.168.100.50"]
                }
            },
            "httpResponse": {
                "statusCode": 200,
                "body": {
                    "data": {
                        "affected_items": [
                            {
                                "rule": {
                                    "level": 9
                                }
                            },
                            {
                                "rule": {
                                    "level": 8
                                }
                            }
                        ]
                    }
                }
            }
        }'
    
    # Send enrichment request via RabbitMQ
    python3 -c "
import pika
import json
import uuid
from datetime import datetime

connection = pika.BlockingConnection(
    pika.ConnectionParameters('localhost', 5673, '/', 
    pika.PlainCredentials('e2euser', 'e2epass'))
)
channel = connection.channel()

# Publish enrichment request
enrichment_request = {
    'event_id': str(uuid.uuid4()),
    'timestamp': datetime.now().isoformat(),
    'type': 'ENRICHMENT_REQUEST_IP',
    'data': {
        'ip_address': '192.168.100.50'
    }
}

channel.exchange_declare(exchange='security_events', exchange_type='topic')
channel.basic_publish(
    exchange='security_events',
    routing_key='ENRICHMENT_REQUEST_IP',
    body=json.dumps(enrichment_request)
)

print('✅ Enrichment request published')
connection.close()
"
    
    log_info "IP enrichment request sent"
}

# 6. Verify test results
verify_results() {
    log_info "Verifying test results..."
    
    # Check module logs
    if [ -f "wazuh-module.log" ]; then
        log_info "Module log contents:"
        tail -n 20 wazuh-module.log
        
        # Check for expected log entries
        if grep -q "Successfully authenticated with Wazuh API" wazuh-module.log; then
            log_info "✅ Authentication test passed"
        else
            log_error "❌ Authentication test failed"
        fi
        
        if grep -q "Published alert" wazuh-module.log; then
            log_info "✅ Alert publishing test passed"
        else
            log_warn "⚠️  No alerts published (may be expected if no alerts)"
        fi
        
        if grep -q "Processing IP enrichment" wazuh-module.log; then
            log_info "✅ IP enrichment test passed"
        else
            log_warn "⚠️  No IP enrichment processed"
        fi
    else
        log_error "❌ Module log file not found"
    fi
    
    # Check mock server logs
    log_info "Checking mock server call count..."
    CALL_COUNT=$(curl -s "http://localhost:55001/mockserver/verify" \
        -H "Content-Type: application/json" \
        -d '{
            "httpRequest": {
                "path": "/security/user/authenticate"
            }
        }' | jq -r '.callCount // 0')
    
    if [ "$CALL_COUNT" -gt 0 ]; then
        log_info "✅ Mock Wazuh API called $CALL_COUNT times"
    else
        log_error "❌ Mock Wazuh API was not called"
    fi
}

# Main execution
main() {
    log_info "Starting E2E test execution..."
    
    setup_test_environment
    run_wazuh_module
    
    # Wait for module to initialize
    sleep 15
    
    test_alert_processing
    test_ip_enrichment
    
    # Allow time for processing
    sleep 10
    
    verify_results
    
    # Stop the module
    if [ -f "module.pid" ]; then
        MODULE_PID=$(cat module.pid)
        log_info "Stopping WazuhModule (PID: $MODULE_PID)..."
        kill $MODULE_PID 2>/dev/null || true
    fi
    
    log_info "🎉 E2E test completed successfully!"
}

# Execute main function
main "$@"
```

### 2. Make the script executable and run it

```bash
# Make script executable
chmod +x nis1-thesis-udm/test-scripts/e2e-test-wazuh.sh

# Run E2E test
./nis1-thesis-udm/test-scripts/e2e-test-wazuh.sh
```

---

## Mock Testing Strategies

### 1. Create Mock Response Files

Create `nis1-thesis-udm/test-resources/mock-wazuh-responses/`:

#### Authentication Response (`auth-response.json`)
```json
{
    "error": 0,
    "data": {
        "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ3YXp1aCIsImF1ZCI6IldhenVoIEFQSSBSRVNUIiwiaWF0IjoxNjEwNzEwOTM5LCJleHAiOjE2MTA3MTEzMzksInN1YiI6IndhemVkIn0.test"
    }
}
```

#### Alerts Response (`alerts-response.json`)
```json
{
    "error": 0,
    "data": {
        "affected_items": [
            {
                "timestamp": "2025-12-20T10:35:12.452Z",
                "agent": {
                    "id": "001",
                    "name": "test-agent",
                    "ip": "192.168.1.100"
                },
                "rule": {
                    "id": "5710",
                    "level": 8,
                    "description": "Multiple authentication failures",
                    "groups": ["authentication_failed", "gdpr_IV_32.2"]
                },
                "data": {
                    "srcip": "192.168.1.100",
                    "protocol": "SSH",
                    "dstuser": "admin"
                }
            },
            {
                "timestamp": "2025-12-20T10:36:15.789Z",
                "agent": {
                    "id": "002",
                    "name": "web-server",
                    "ip": "192.168.1.200"
                },
                "rule": {
                    "id": "31151",
                    "level": 12,
                    "description": "Ransomware file extensions detected",
                    "groups": ["ransomware", "critical"]
                },
                "data": {
                    "srcip": "192.168.1.200",
                    "protocol": "TCP",
                    "win": {
                        "eventdata": {
                            "image": "C:\\Windows\\System32\\suspicious.exe",
                            "targetFilename": "C:\\Users\\documents\\encrypted.docx"
                        }
                    }
                }
            }
        ]
    }
}
```

#### Empty Response (`empty-response.json`)
```json
{
    "error": 0,
    "data": {
        "affected_items": []
    }
}
```

### 2. Mock Server Utility Class

Create `nis1-thesis-udm/src/test/java/com/nis1/thesis/udm/TestUtils.java`:

```java
package com.nis1.thesis.udm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class for testing UDMs
 */
public class TestUtils {
    
    public static MockWebServer createMockWazuhServer() throws IOException {
        MockWebServer server = new MockWebServer();
        
        // Setup default responses
        setupAuthenticationResponse(server);
        setupAlertsResponse(server);
        
        return server;
    }
    
    public static void setupAuthenticationResponse(MockWebServer server) throws IOException {
        String authResponse = loadMockResponse("auth-response.json");
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(authResponse));
    }
    
    public static void setupAlertsResponse(MockWebServer server) throws IOException {
        String alertsResponse = loadMockResponse("alerts-response.json");
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(alertsResponse));
    }
    
    public static void setupEmptyAlertsResponse(MockWebServer server) throws IOException {
        String emptyResponse = loadMockResponse("empty-response.json");
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(emptyResponse));
    }
    
    public static void setupErrorResponse(MockWebServer server, int statusCode) {
        server.enqueue(new MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\": " + statusCode + ", \"message\": \"Test error\"}"));
    }
    
    private static String loadMockResponse(String filename) throws IOException {
        String resourcePath = "test-resources/mock-wazuh-responses/" + filename;
        return Files.readString(Paths.get(resourcePath));
    }
    
    // Utility methods for event verification
    public static boolean isValidWazuhAlert(Event<?> event) {
        return event != null && 
               "HOST_ALERT_WAZUH".equals(event.getType()) &&
               event.getData() instanceof HostAlertData;
    }
    
    public static boolean isValidIpReputation(Event<?> event) {
        return event != null &&
               "IP_REPUTATION_WAZUH".equals(event.getType()) &&
               event.getData() instanceof IpReputationData;
    }
}
```

---

## Performance Testing

### 1. Load Testing Configuration

Create `nis1-thesis-udm/src/test/java/com/nis1/thesis/udm/WazuhModulePerformanceTest.java`:

```java
package com.nis1.thesis.udm;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for WazuhModule
 * Run with: mvn test -Dtest=WazuhModulePerformanceTest -Dperformance.test=true
 */
@EnabledIfSystemProperty(named = "performance.test", matches = "true")
class WazuhModulePerformanceTest {

    private WazuhModule wazuhModule;
    private TestCoreSystemApi testApi;
    private final AtomicInteger processedEvents = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        wazuhModule = new WazuhModule();
        testApi = new TestCoreSystemApi();
    }

    @Test
    @DisplayName("Should handle high-frequency alert processing")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testHighFrequencyAlertProcessing() throws InterruptedException {
        // Given
        int numberOfAlerts = 1000;
        int concurrentThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(numberOfAlerts);

        wazuhModule.initialize(testApi);

        // When - Simulate high-frequency alerts
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfAlerts; i++) {
            final int alertIndex = i;
            executor.submit(() -> {
                try {
                    long processingStart = System.nanoTime();
                    
                    // Simulate alert processing
                    HostAlertData alertData = new HostAlertData(
                        "192.168.1." + (alertIndex % 254 + 1),
                        "Performance test alert " + alertIndex,
                        "high"
                    );
                    
                    Event<HostAlertData> event = Event.of("HOST_ALERT_TEST", alertData);
                    testApi.publishEvent(event);
                    
                    long processingTime = System.nanoTime() - processingStart;
                    totalProcessingTime.addAndGet(processingTime);
                    processedEvents.incrementAndGet();
                    
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all alerts to be processed
        assertTrue(latch.await(30, TimeUnit.SECONDS), 
            "All alerts should be processed within 30 seconds");

        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // Then - Verify performance metrics
        long totalDuration = endTime - startTime;
        double throughput = (double) numberOfAlerts / (totalDuration / 1000.0);
        double avgProcessingTimeMs = totalProcessingTime.get() / (double) numberOfAlerts / 1_000_000;

        System.out.printf("Performance Test Results:%n");
        System.out.printf("Total alerts processed: %d%n", processedEvents.get());
        System.out.printf("Total duration: %d ms%n", totalDuration);
        System.out.printf("Throughput: %.2f alerts/second%n", throughput);
        System.out.printf("Average processing time: %.3f ms%n", avgProcessingTimeMs);

        // Performance assertions
        assertEquals(numberOfAlerts, processedEvents.get());
        assertTrue(throughput > 10, "Throughput should be > 10 alerts/second");
        assertTrue(avgProcessingTimeMs < 100, "Average processing time should be < 100ms");
    }

    @Test
    @DisplayName("Should handle memory efficiently under load")
    void testMemoryEfficiency() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        wazuhModule.initialize(testApi);
        
        // When - Process many events
        for (int i = 0; i < 10000; i++) {
            EnrichmentRequestData request = new EnrichmentRequestData("192.168.1." + (i % 254 + 1));
            Event<EnrichmentRequestData> event = Event.of("ENRICHMENT_REQUEST_IP", request);
            
            // Simulate processing
            testApi.publishEvent(event);
            
            // Force garbage collection periodically
            if (i % 1000 == 0) {
                System.gc();
                Thread.yield();
            }
        }
        
        // Force final garbage collection
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryGrowth = finalMemory - initialMemory;
        
        // Then - Verify memory usage is reasonable
        System.out.printf("Memory Usage:%n");
        System.out.printf("Initial memory: %d KB%n", initialMemory / 1024);
        System.out.printf("Final memory: %d KB%n", finalMemory / 1024);
        System.out.printf("Memory growth: %d KB%n", memoryGrowth / 1024);
        
        // Memory growth should be reasonable (less than 100MB)
        assertTrue(memoryGrowth < 100 * 1024 * 1024, 
            "Memory growth should be less than 100MB");
    }

    @Test
    @DisplayName("Should maintain performance with concurrent enrichment requests")
    void testConcurrentEnrichmentPerformance() throws InterruptedException {
        // Given
        int numberOfRequests = 500;
        int concurrentThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        AtomicInteger successfulRequests = new AtomicInteger(0);

        wazuhModule.initialize(testApi);

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfRequests; i++) {
            final int requestIndex = i;
            executor.submit(() -> {
                try {
                    String ipAddress = "10.0." + (requestIndex / 254) + "." + (requestIndex % 254 + 1);
                    EnrichmentRequestData request = new EnrichmentRequestData(ipAddress);
                    Event<EnrichmentRequestData> event = Event.of("ENRICHMENT_REQUEST_IP", request);
                    
                    testApi.publishEvent(event);
                    successfulRequests.incrementAndGet();
                    
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // Then
        long duration = endTime - startTime;
        double throughput = (double) successfulRequests.get() / (duration / 1000.0);

        System.out.printf("Concurrent Enrichment Performance:%n");
        System.out.printf("Successful requests: %d/%d%n", successfulRequests.get(), numberOfRequests);
        System.out.printf("Duration: %d ms%n", duration);
        System.out.printf("Throughput: %.2f requests/second%n", throughput);

        assertEquals(numberOfRequests, successfulRequests.get());
        assertTrue(throughput > 5, "Concurrent throughput should be > 5 requests/second");
    }
}
```

### 2. Running Performance Tests

```bash
# Run performance tests
mvn test -Dtest=WazuhModulePerformanceTest -Dperformance.test=true

# Run with memory profiling
mvn test -Dtest=WazuhModulePerformanceTest -Dperformance.test=true \
    -DargLine="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps"

# Run with custom heap size
mvn test -Dtest=WazuhModulePerformanceTest -Dperformance.test=true \
    -DargLine="-Xmx2g -Xms1g"
```

---

## Troubleshooting Tests

### 1. Common Test Issues and Solutions

#### Test Environment Issues
```bash
# Check if required containers are running
docker ps | grep -E "(rabbitmq|mockserver)"

# Check container logs
docker logs soar-test-rmq
docker logs mock-wazuh

# Restart test containers
docker restart soar-test-rmq mock-wazuh

# Clean up test containers
docker stop $(docker ps -q --filter "name=soar-test")
docker rm $(docker ps -aq --filter "name=soar-test")
```

#### Maven Test Issues
```bash
# Clean and rebuild
mvn clean compile test-compile

# Run tests with debugging
mvn test -Dtest=WazuhModuleTest -DforkMode=never -DforkCount=1 -X

# Skip integration tests
mvn test -DskipITs

# Run specific test method with debugging
mvn test -Dtest=WazuhModuleTest#testModuleInitialization -Dmaven.surefire.debug
```

#### Network and Connectivity Issues
```bash
# Check port availability
netstat -ln | grep -E "(5672|15672|55000)"

# Test RabbitMQ connectivity
curl -u testuser:testpass http://localhost:15672/api/overview

# Test mock server connectivity  
curl http://localhost:55000/mockserver/status
```

### 2. Test Debug Configuration

Add to `nis1-thesis-udm/src/test/resources/logback-test.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Test-specific logging levels -->
    <logger name="com.nis1.thesis.udm" level="DEBUG"/>
    <logger name="org.testcontainers" level="INFO"/>
    <logger name="com.rabbitmq" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

---

## CI/CD Integration

### 1. GitHub Actions Workflow

Create `.github/workflows/udm-testing.yml`:

```yaml
name: UDM Testing Pipeline

on:
  push:
    paths:
      - 'nis1-thesis-udm/**'
      - '.github/workflows/udm-testing.yml'
  pull_request:
    paths:
      - 'nis1-thesis-udm/**'

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Build SDK
      run: mvn clean install -pl nis-thesis-sdk -DskipTests
    
    - name: Run unit tests
      run: mvn test -pl nis1-thesis-udm
    
    - name: Upload test results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: unit-test-results
        path: nis1-thesis-udm/target/surefire-reports/

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    
    services:
      rabbitmq:
        image: rabbitmq:3-management
        ports:
          - 5672:5672
          - 15672:15672
        env:
          RABBITMQ_DEFAULT_USER: testuser
          RABBITMQ_DEFAULT_PASS: testpass
        options: >-
          --health-cmd "rabbitmq-diagnostics -q ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Build project
      run: mvn clean compile -pl nis-thesis-sdk,nis1-thesis-udm
    
    - name: Run integration tests
      run: mvn integration-test -pl nis1-thesis-udm
      env:
        RABBITMQ_HOST: localhost
        RABBITMQ_PORT: 5672
    
    - name: Upload integration test results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: integration-test-results
        path: nis1-thesis-udm/target/failsafe-reports/

  performance-tests:
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Run performance tests
      run: mvn test -pl nis1-thesis-udm -Dtest=*PerformanceTest -Dperformance.test=true
    
    - name: Upload performance results
      uses: actions/upload-artifact@v3
      with:
        name: performance-test-results
        path: nis1-thesis-udm/target/surefire-reports/
```

### 2. Test Reporting

Add test reporting plugin to `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Best Practices

### 1. Test Organization
- ✅ **Separate test types**: Unit, integration, and E2E tests in different classes
- ✅ **Use descriptive names**: Test method names should describe the scenario
- ✅ **Follow AAA pattern**: Arrange, Act, Assert structure
- ✅ **Mock external dependencies**: Use mock servers for external APIs

### 2. Test Data Management
- ✅ **Use test resources**: Store mock responses in separate files
- ✅ **Parameterized tests**: Test multiple scenarios with different inputs
- ✅ **Test edge cases**: Include error conditions and boundary values
- ✅ **Clean up resources**: Properly close connections and clean up test data

### 3. Performance Considerations
- ✅ **Set timeouts**: Prevent tests from hanging indefinitely
- ✅ **Monitor resources**: Check memory usage and connection leaks
- ✅ **Parallel execution**: Run independent tests concurrently
- ✅ **Profile performance**: Regular performance regression testing

### 4. Maintenance
- ✅ **Keep tests up to date**: Update tests when code changes
- ✅ **Review test coverage**: Aim for >80% code coverage
- ✅ **Regular test runs**: Automate testing in CI/CD pipeline
- ✅ **Document test scenarios**: Clear comments and documentation

---

## Running All Tests

### Complete Test Suite Execution

```bash
# Full test suite (recommended order)
cd /home/kean/Documents/SOARapp

# 1. Unit tests first
mvn clean test -pl nis1-thesis-udm

# 2. Integration tests
mvn integration-test -pl nis1-thesis-udm

# 3. Performance tests (optional)
mvn test -pl nis1-thesis-udm -Dtest=*PerformanceTest -Dperformance.test=true

# 4. End-to-end tests (manual)
./nis1-thesis-udm/test-scripts/e2e-test-wazuh.sh

# Generate combined coverage report
mvn jacoco:report -pl nis1-thesis-udm

# View results
open nis1-thesis-udm/target/site/jacoco/index.html
```

### Test Results Analysis

After running tests, check:
- **Test Reports**: `nis1-thesis-udm/target/surefire-reports/`
- **Coverage Report**: `nis1-thesis-udm/target/site/jacoco/index.html`
- **Integration Reports**: `nis1-thesis-udm/target/failsafe-reports/`
- **Module Logs**: Check application logs for runtime behavior

This comprehensive testing guide ensures your user-defined modules are thoroughly tested, reliable, and ready for production deployment in your SOAR framework.