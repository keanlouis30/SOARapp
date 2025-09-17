package com.yourorg.middleware;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WazuhAlertDao {

    public void insertAlert(WazuhAlert alert) throws SQLException {
        String sql = "INSERT INTO wazuh_alerts (event_id, timestamp, event_type, source_module, payload) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, alert.getEventId());
            stmt.setObject(2, alert.getTimestamp());
            stmt.setString(3, alert.getEventType());
            stmt.setString(4, alert.getSourceModule());
            stmt.setString(5, alert.getPayloadJson());
            stmt.executeUpdate();
        }
    }

    public List<WazuhAlert> findBySeverity(String severity) throws SQLException {
        List<WazuhAlert> alerts = new ArrayList<>();
        String sql = "SELECT event_id, timestamp, event_type, source_module, payload::text " +
                     "FROM wazuh_alerts WHERE payload->>'severity' = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, severity);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    WazuhAlert alert = new WazuhAlert();
                    alert.setEventId((UUID) rs.getObject("event_id"));
                    alert.setTimestamp(rs.getObject("timestamp", java.time.OffsetDateTime.class));
                    alert.setEventType(rs.getString("event_type"));
                    alert.setSourceModule(rs.getString("source_module"));
                    alert.setPayloadJson(rs.getString("payload"));
                    alerts.add(alert);
                }
            }
        }
        return alerts;
    }

    public List<WazuhAlert> findAll() throws SQLException {
        List<WazuhAlert> alerts = new ArrayList<>();
        String sql = "SELECT event_id, timestamp, event_type, source_module, payload::text FROM wazuh_alerts";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                WazuhAlert alert = new WazuhAlert();
                alert.setEventId((UUID) rs.getObject("event_id"));
                alert.setTimestamp(rs.getObject("timestamp", java.time.OffsetDateTime.class));
                alert.setEventType(rs.getString("event_type"));
                alert.setSourceModule(rs.getString("source_module"));
                alert.setPayloadJson(rs.getString("payload"));
                alerts.add(alert);
            }
        }
        return alerts;
    }
}
