package com.yourorg.middleware;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class WazuhAlertDao {
    public void insertAlert(String payloadJson) {
        String sql = "INSERT INTO wazuh_alerts (event_id, timestamp, event_type, source_module, payload) " +
                     "VALUES (?::uuid, NOW(), ?, ?, ?::json)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, UUID.randomUUID().toString());   // event_id
            ps.setString(2, "alerts.host.wazuh");            // event_type
            ps.setString(3, "MiddlewareSender");             // source_module
            ps.setString(4, payloadJson);                    // payload JSON
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Error inserting alert", e);
        }
    }

    public void findBySeverity(String severity) {
        String sql = "SELECT event_id, event_type, source_module, payload " +
                     "FROM wazuh_alerts " +
                     "WHERE payload->>'severity' = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, severity);
            ResultSet rs = ps.executeQuery();

            System.out.println("🔎 Alerts with severity = " + severity);
            while (rs.next()) {
                String id = rs.getString("event_id");
                String type = rs.getString("event_type");
                String source = rs.getString("source_module");
                String payload = rs.getString("payload");

                System.out.println("ID: " + id);
                System.out.println("Type: " + type);
                System.out.println("Source: " + source);
                System.out.println("Payload: " + payload);
                System.out.println("--------------------------------------------------");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error querying alerts", e);
        }
    }
}
