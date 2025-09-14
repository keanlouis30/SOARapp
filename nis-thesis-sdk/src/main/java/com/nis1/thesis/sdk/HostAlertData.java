package com.nis1.thesis.sdk;

/**
 * Payload for a host alert event (Phase 1 POC).
 */
public class HostAlertData {
    private String sourceIp;
    private String description;
    private String severity;

    public HostAlertData() {}

    public HostAlertData(String sourceIp, String description, String severity) {
        this.sourceIp = sourceIp;
        this.description = description;
        this.severity = severity;
    }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
