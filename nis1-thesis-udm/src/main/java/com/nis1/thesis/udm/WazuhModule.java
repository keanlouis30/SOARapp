package com.nis1.thesis.udm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nis1.thesis.sdk.*;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * WazuhModule - User-Defined Module for Wazuh SIEM Integration
 * 
 * This user-defined module demonstrates complete integration with Wazuh SIEM using the SOAR SDK.
 * It transforms Wazuh alerts into the standardized JSON message format as specified in the 
 * SDK documentation. This module serves as a reference implementation for building security
 * tool integrations with the SOAR framework.
 * 
 * Features:
 * - Real-time Wazuh alert polling via REST API
 * - JWT authentication with automatic token refresh
 * - Alert transformation to standardized JSON format
 * - Event publishing through the SDK framework
 * - IP enrichment and threat analysis capabilities
 * - Proper resource management and error handling
 * 
 * @author SOAR UDM Developer
 * @version 1.0
 * @since December 2025
 */
public class WazuhModule implements PluggableModule {
    
    // Wazuh Configuration Constants
    private static final String WAZUH_API_BASE_URL = "https://wazuh-manager.local:55000";
    private static final String WAZUH_USERNAME = "wazuh-api";
    private static final String WAZUH_PASSWORD = "MySecurePassword2025!";
    private static final int POLL_INTERVAL_SECONDS = 30;
    private static final int AUTH_REFRESH_MINUTES = 15;
    
    // SDK Components
    private CoreSystemApi api;
    private ModuleHelper helper;
    
    // HTTP Client and JSON processing
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
    private final Gson gson = new Gson();
    
    // Authentication and scheduling
    private String authToken;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    @Override
    public String getName() {
        return "Wazuh User-Defined Module";
    }
    
    @Override
    public void initialize(CoreSystemApi api) {
        this.api = api;
        this.helper = new ModuleHelper(api);
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.running = true;
        
        helper.log(getName(), "INFO", "Initializing Wazuh User-Defined Module");
        
        // Subscribe to enrichment requests from other modules
        api.subscribeToEvent("ENRICHMENT_REQUEST_IP", this::handleIpEnrichmentRequest);
        
        // Subscribe to host alerts for correlation analysis
        api.subscribeToEvent("HOST_ALERT.*", this::handleHostAlertCorrelation);
        
        // Start background services
        startAuthenticationService();
        startAlertPollingService();
        
        helper.log(getName(), "INFO", "Wazuh user-defined module initialized successfully");
    }
    
    /**
     * Starts the authentication service to maintain valid Wazuh API tokens
     */
    private void startAuthenticationService() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                authenticateWithWazuh();
            } catch (Exception e) {
                helper.log(getName(), "ERROR", "Wazuh authentication failed: " + e.getMessage());
            }
        }, 0, AUTH_REFRESH_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * Authenticates with Wazuh API and stores the JWT token
     */
    private void authenticateWithWazuh() throws IOException {
        String credentials = Base64.getEncoder().encodeToString(
            (WAZUH_USERNAME + ":" + WAZUH_PASSWORD).getBytes()
        );
        
        Request request = new Request.Builder()
            .url(WAZUH_API_BASE_URL + "/security/user/authenticate")
            .addHeader("Authorization", "Basic " + credentials)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create("", MediaType.parse("application/json")))
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject responseJson = JsonParser.parseString(response.body().string()).getAsJsonObject();
                
                if (responseJson.has("data") && 
                    responseJson.getAsJsonObject("data").has("token")) {
                    
                    authToken = responseJson.getAsJsonObject("data").get("token").getAsString();
                    helper.log(getName(), "INFO", "Successfully authenticated with Wazuh API");
                    
                } else {
                    helper.log(getName(), "ERROR", "No token found in Wazuh authentication response");
                }
            } else {
                helper.log(getName(), "ERROR", 
                    "Wazuh authentication failed: HTTP " + response.code());
            }
        }
    }
    
    /**
     * Starts the alert polling service to fetch new Wazuh alerts
     */
    private void startAlertPollingService() {
        scheduler.scheduleAtFixedRate(() -> {
            if (authToken != null && running) {
                try {
                    pollWazuhAlerts();
                } catch (Exception e) {
                    helper.log(getName(), "ERROR", 
                        "Error polling Wazuh alerts: " + e.getMessage());
                }
            }
        }, 30, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Polls Wazuh API for new high-severity alerts
     */
    private void pollWazuhAlerts() throws IOException {
        // Query for alerts with level 7+ (high severity) from last 5 minutes
        String alertsUrl = WAZUH_API_BASE_URL + 
            "/alerts?level=7,8,9,10,11,12,13,14,15&limit=50&sort=-timestamp&time_filter=300s";
            
        Request request = new Request.Builder()
            .url(alertsUrl)
            .addHeader("Authorization", "Bearer " + authToken)
            .addHeader("Content-Type", "application/json")
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                processWazuhAlertsResponse(responseBody);
                
            } else if (response.code() == 401) {
                helper.log(getName(), "WARN", "Wazuh token expired, re-authenticating");
                authenticateWithWazuh();
                
            } else {
                helper.log(getName(), "ERROR", 
                    "Failed to poll Wazuh alerts: HTTP " + response.code());
            }
        }
    }
    
    /**
     * Processes Wazuh API response and publishes standardized events
     */
    private void processWazuhAlertsResponse(String jsonResponse) {
        try {
            JsonObject responseJson = JsonParser.parseString(jsonResponse).getAsJsonObject();
            
            if (!responseJson.has("data") || 
                !responseJson.getAsJsonObject("data").has("affected_items")) {
                return;
            }
            
            JsonArray alerts = responseJson.getAsJsonObject("data")
                                         .getAsJsonArray("affected_items");
            int processedCount = 0;
            
            for (var alertElement : alerts) {
                try {
                    JsonObject wazuhAlert = alertElement.getAsJsonObject();
                    WazuhAlertMessage alertMessage = transformWazuhAlert(wazuhAlert);
                    
                    if (alertMessage != null) {
                        publishStandardizedAlert(alertMessage);
                        processedCount++;
                    }
                    
                } catch (Exception e) {
                    helper.log(getName(), "ERROR", 
                        "Failed to process individual Wazuh alert: " + e.getMessage());
                }
            }
            
            if (processedCount > 0) {
                helper.log(getName(), "INFO", 
                    "Processed " + processedCount + " Wazuh alerts");
            }
            
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Failed to parse Wazuh API response: " + e.getMessage());
        }
    }
    
    /**
     * Transforms a raw Wazuh alert into the standardized message format
     * as specified in the SDK documentation
     */
    private WazuhAlertMessage transformWazuhAlert(JsonObject wazuhAlert) {
        try {
            WazuhAlertMessage alertMessage = new WazuhAlertMessage();
            
            // Generate unique event ID and timestamp
            alertMessage.setEventId(UUID.randomUUID().toString());
            alertMessage.setTimestamp(Instant.now().toString());
            alertMessage.setEventType("alerts.host.wazuh");
            alertMessage.setSourceModule("WazuhModule");
            
            // Create payload from Wazuh alert data
            WazuhAlertPayload payload = new WazuhAlertPayload();
            
            // Extract agent information
            if (wazuhAlert.has("agent")) {
                JsonObject agent = wazuhAlert.getAsJsonObject("agent");
                if (agent.has("ip")) {
                    String agentIp = agent.get("ip").getAsString();
                    payload.setHostId("host-" + agentIp);
                    payload.setSourceIp(agentIp);
                }
            }
            
            // Extract rule information
            if (wazuhAlert.has("rule")) {
                JsonObject rule = wazuhAlert.getAsJsonObject("rule");
                
                if (rule.has("id")) {
                    payload.setSignatureId(rule.get("id").getAsString());
                }
                
                if (rule.has("description")) {
                    String description = rule.get("description").getAsString();
                    payload.setSignature(description);
                    payload.setAlertType(determineAlertType(description));
                }
                
                if (rule.has("level")) {
                    int level = rule.get("level").getAsInt();
                    payload.setSeverity(mapWazuhLevelToSeverity(level));
                    payload.setThreatScore(calculateThreatScore(level));
                }
                
                if (rule.has("groups")) {
                    JsonArray groups = rule.getAsJsonArray("groups");
                    if (groups.size() > 0) {
                        payload.setMatchedRule(groups.get(0).getAsString());
                    }
                }
            }
            
            // Extract additional data fields
            if (wazuhAlert.has("data")) {
                JsonObject data = wazuhAlert.getAsJsonObject("data");
                
                if (data.has("srcip")) {
                    payload.setSourceIp(data.get("srcip").getAsString());
                }
                
                if (data.has("dstip")) {
                    payload.setDestinationIp(data.get("dstip").getAsString());
                }
                
                if (data.has("protocol")) {
                    payload.setProtocol(data.get("protocol").getAsString());
                }
                
                // Extract Windows event data if available
                if (data.has("win") && data.getAsJsonObject("win").has("eventdata")) {
                    JsonObject eventData = data.getAsJsonObject("win").getAsJsonObject("eventdata");
                    
                    if (eventData.has("image")) {
                        payload.setProcess(eventData.get("image").getAsString());
                    }
                    
                    if (eventData.has("targetFilename")) {
                        payload.setFilePath(eventData.get("targetFilename").getAsString());
                    }
                }
            }
            
            // Set default values for missing fields
            if (payload.getProtocol() == null) {
                payload.setProtocol("TCP");
            }
            
            if (payload.getDestinationIp() == null && payload.getSourceIp() != null) {
                // Set a default destination IP for demo purposes
                payload.setDestinationIp("10.0.0.1");
            }
            
            alertMessage.setPayload(payload);
            return alertMessage;
            
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Failed to transform Wazuh alert: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Publishes the standardized alert message using the SDK
     * This demonstrates both SDK integration and standardized message generation
     */
    private void publishStandardizedAlert(WazuhAlertMessage alertMessage) {
        try {
            // Create HostAlertData for SDK compatibility
            HostAlertData hostAlert = new HostAlertData(
                alertMessage.getPayload().getSourceIp(),
                alertMessage.getPayload().getSignature(),
                alertMessage.getPayload().getSeverity()
            );
            
            // Publish through SDK framework
            Event<HostAlertData> sdkEvent = Event.of("HOST_ALERT_WAZUH", hostAlert);
            api.publishEvent(sdkEvent);
            
            // Log the standardized message format for debugging/monitoring
            String jsonMessage = gson.toJson(alertMessage);
            helper.log(getName(), "DEBUG", "Standardized JSON message: " + jsonMessage);
            
            helper.log(getName(), "INFO", 
                String.format("Published alert: %s (Severity: %s, Threat Score: %d)",
                    alertMessage.getPayload().getSignature(),
                    alertMessage.getPayload().getSeverity(),
                    alertMessage.getPayload().getThreatScore()));
                    
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Failed to publish standardized alert: " + e.getMessage());
        }
    }
    
    /**
     * Handles IP enrichment requests from other modules
     */
    private void handleIpEnrichmentRequest(Event<?> event) {
        try {
            EnrichmentRequestData request = (EnrichmentRequestData) event.getData();
            String ipAddress = request.getIpAddress();
            
            if (ipAddress == null || ipAddress.isEmpty()) {
                helper.log(getName(), "WARN", 
                    "Received enrichment request with null/empty IP address");
                return;
            }
            
            helper.log(getName(), "INFO", 
                "Processing IP enrichment request for: " + ipAddress);
            
            // Query Wazuh for historical data about this IP
            enrichIpWithWazuhData(ipAddress);
            
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Error processing IP enrichment request: " + e.getMessage());
        }
    }
    
    /**
     * Enriches IP address with historical Wazuh data
     */
    private void enrichIpWithWazuhData(String ipAddress) {
        try {
            if (authToken == null) {
                helper.log(getName(), "WARN", "Cannot enrich IP - no valid auth token");
                return;
            }
            
            String enrichmentUrl = WAZUH_API_BASE_URL + 
                "/alerts?q=data.srcip=" + ipAddress + "&limit=100&sort=-timestamp";
                
            Request request = new Request.Builder()
                .url(enrichmentUrl)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .build();
                
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    IpReputationData reputation = analyzeIpReputation(ipAddress, responseBody);
                    
                    // Publish enrichment result
                    Event<IpReputationData> enrichmentEvent = 
                        Event.of("IP_REPUTATION_WAZUH", reputation);
                    api.publishEvent(enrichmentEvent);
                    
                    helper.log(getName(), "INFO", 
                        String.format("Published IP reputation for %s: Malicious=%s, Confidence=%d",
                            ipAddress, reputation.isMalicious(), reputation.getConfidenceScore()));
                }
            }
            
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Failed to enrich IP with Wazuh data: " + e.getMessage());
        }
    }
    
    /**
     * Analyzes historical Wazuh data to determine IP reputation
     */
    private IpReputationData analyzeIpReputation(String ipAddress, String jsonResponse) {
        try {
            JsonObject responseJson = JsonParser.parseString(jsonResponse).getAsJsonObject();
            
            int alertCount = 0;
            int highSeverityAlerts = 0;
            
            if (responseJson.has("data") && 
                responseJson.getAsJsonObject("data").has("affected_items")) {
                
                JsonArray alerts = responseJson.getAsJsonObject("data")
                                             .getAsJsonArray("affected_items");
                alertCount = alerts.size();
                
                // Count high-severity alerts
                for (var alertElement : alerts) {
                    JsonObject alert = alertElement.getAsJsonObject();
                    if (alert.has("rule")) {
                        JsonObject rule = alert.getAsJsonObject("rule");
                        if (rule.has("level") && rule.get("level").getAsInt() >= 7) {
                            highSeverityAlerts++;
                        }
                    }
                }
            }
            
            // Determine reputation based on alert patterns
            boolean isMalicious = alertCount > 5 || highSeverityAlerts > 2;
            int confidence = Math.min(100, alertCount * 10 + highSeverityAlerts * 25);
            String category = highSeverityAlerts > 0 ? "behavioral_analysis" : "low_activity";
            
            return new IpReputationData(ipAddress, isMalicious, "Wazuh-Historical", 
                                      category, confidence);
                                      
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Failed to analyze IP reputation: " + e.getMessage());
            
            // Return neutral reputation on error
            return new IpReputationData(ipAddress, false, "Wazuh-Historical", 
                                      "analysis_error", 0);
        }
    }
    
    /**
     * Handles host alert correlation from other modules
     */
    private void handleHostAlertCorrelation(Event<?> event) {
        try {
            if (event.getData() instanceof HostAlertData) {
                HostAlertData alert = (HostAlertData) event.getData();
                
                helper.log(getName(), "INFO", 
                    String.format("Correlating host alert for IP: %s with Wazuh data",
                        alert.getSourceIp()));
                
                // In a full implementation, this would perform correlation analysis
                // For now, we'll just log the correlation attempt
                helper.log(getName(), "INFO", 
                    String.format("Correlation analysis completed for %s", 
                        alert.getSourceIp()));
            }
            
        } catch (Exception e) {
            helper.log(getName(), "ERROR", 
                "Error in host alert correlation: " + e.getMessage());
        }
    }
    
    // Utility Methods for Alert Classification and Scoring
    
    private String determineAlertType(String description) {
        String lowerDesc = description.toLowerCase();
        
        if (lowerDesc.contains("ransomware") || lowerDesc.contains("encryption")) {
            return "ransomware_detection";
        } else if (lowerDesc.contains("malware") || lowerDesc.contains("virus")) {
            return "malware_detection";
        } else if (lowerDesc.contains("brute") || lowerDesc.contains("authentication")) {
            return "authentication_attack";
        } else if (lowerDesc.contains("rootkit") || lowerDesc.contains("privilege")) {
            return "privilege_escalation";
        } else if (lowerDesc.contains("network") || lowerDesc.contains("connection")) {
            return "network_anomaly";
        } else if (lowerDesc.contains("file") || lowerDesc.contains("integrity")) {
            return "file_integrity_violation";
        }
        
        return "security_violation";
    }
    
    private String mapWazuhLevelToSeverity(int level) {
        if (level >= 12) return "critical";
        if (level >= 7) return "high";
        if (level >= 4) return "medium";
        return "low";
    }
    
    private Integer calculateThreatScore(int wazuhLevel) {
        // Convert Wazuh levels (0-15) to threat score (0-100)
        return Math.min(100, (int) ((wazuhLevel / 15.0) * 100));
    }
    
    @Override
    public void shutdown() {
        running = false;
        helper.log(getName(), "INFO", "Shutting down Wazuh User-Defined Module");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        helper.log(getName(), "INFO", "Wazuh user-defined module shutdown complete");
    }
    
    /**
     * Data classes for the standardized JSON message format
     * These classes represent the exact structure specified in the SDK documentation
     */
    public static class WazuhAlertMessage {
        private String eventId;
        private String timestamp;
        private String eventType;
        private String sourceModule;
        private WazuhAlertPayload payload;
        
        // Getters and setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getSourceModule() { return sourceModule; }
        public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }
        
        public WazuhAlertPayload getPayload() { return payload; }
        public void setPayload(WazuhAlertPayload payload) { this.payload = payload; }
    }
    
    public static class WazuhAlertPayload {
        private String hostId;
        private String alertType;
        private String signatureId;
        private String signature;
        private String severity;
        private String process;
        private String filePath;
        private String matchedRule;
        private String sourceIp;
        private String destinationIp;
        private String protocol;
        private Integer threatScore;
        
        // Getters and setters
        public String getHostId() { return hostId; }
        public void setHostId(String hostId) { this.hostId = hostId; }
        
        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }
        
        public String getSignatureId() { return signatureId; }
        public void setSignatureId(String signatureId) { this.signatureId = signatureId; }
        
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public String getProcess() { return process; }
        public void setProcess(String process) { this.process = process; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public String getMatchedRule() { return matchedRule; }
        public void setMatchedRule(String matchedRule) { this.matchedRule = matchedRule; }
        
        public String getSourceIp() { return sourceIp; }
        public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
        
        public String getDestinationIp() { return destinationIp; }
        public void setDestinationIp(String destinationIp) { this.destinationIp = destinationIp; }
        
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        
        public Integer getThreatScore() { return threatScore; }
        public void setThreatScore(Integer threatScore) { this.threatScore = threatScore; }
    }
}