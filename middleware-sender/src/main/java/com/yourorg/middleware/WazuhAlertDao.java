package com.yourorg.middleware;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;

public class WazuhAlertDao {

    // No-arg constructor
    public WazuhAlertDao() {
        // Nothing needed; connection is obtained in each method
    }

    /**
     * Insert an alert JSON into PostgreSQL.
     * event_id = timestamp + event_type + random UUID
     */
    public void insertAlert(JSONObject json) {
    String sql = "INSERT INTO wazuh_alerts (log_id, event_id, timestamp, event_type, source_module, payload) " +
                 "VALUES (?, ?, ?::timestamptz, ?, ?, ?::jsonb)";

    try (Connection conn = DatabaseUtil.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        // Extract values from JSON
        String eventId = json.getString("event_id");  // retain event_id from JSON
        String ts = json.getString("timestamp");      // timestamp string with milliseconds
        String eventType = json.getString("event_type");
        String sourceModule = json.getString("source_module");

        // Generate unique log_id
        String logId = ts + "_" + eventType + "_" + UUID.randomUUID();

        stmt.setString(1, logId);
        stmt.setString(2, eventId);
        stmt.setString(3, ts);
        stmt.setString(4, eventType);
        stmt.setString(5, sourceModule);
        stmt.setString(6, json.toString());

        stmt.executeUpdate();
        System.out.println("✔ Inserted alert with log_id: " + logId);

    } catch (SQLException e) {
        throw new RuntimeException("Error inserting alert", e);
    }
}

    /**
     * Query alerts by severity inside the payload.
     */
    public List<JSONObject> findBySeverity(String severity) {
        List<JSONObject> alerts = new ArrayList<>();
        String sql = "SELECT event_id, timestamp, event_type, source_module, payload " +
                     "FROM wazuh_alerts " +
                     "WHERE payload->>'severity' = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, severity);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject json = new JSONObject(rs.getString("payload"));
                    json.put("event_id", rs.getString("event_id"));
                    json.put("timestamp", rs.getString("timestamp"));
                    json.put("event_type", rs.getString("event_type"));
                    json.put("source_module", rs.getString("source_module"));
                    alerts.add(json);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error querying alerts", e);
        }

        return alerts;
    }
}
