package com.yourorg.middleware;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class DbDemo {
    public static void main(String[] args) {
        WazuhAlertDao dao = new WazuhAlertDao();

        try {
            // Insert a sample alert
            WazuhAlert alert = new WazuhAlert();
            alert.setEventId(UUID.randomUUID());
            alert.setTimestamp(OffsetDateTime.now());
            alert.setEventType("alerts.host.wazuh");
            alert.setSourceModule("WazuhConnector");
            alert.setPayloadJson("{\"host_id\":\"host-192.168.1.101\",\"severity\":\"high\",\"alert_type\":\"ransomware_detection\"}");
            dao.insertAlert(alert);
            System.out.println("✅ Alert inserted into DB");

            // Query by severity
            List<WazuhAlert> highAlerts = dao.findBySeverity("high");
            System.out.println("🔎 Found " + highAlerts.size() + " high severity alerts:");
            highAlerts.forEach(a -> System.out.println(a.getEventId() + " | " + a.getPayloadJson()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
