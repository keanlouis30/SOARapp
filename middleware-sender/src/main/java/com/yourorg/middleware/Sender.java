package com.yourorg.middleware;

import java.util.UUID;

public class Sender {
    public static void main(String[] args) {
        String[] alerts = new String[10];

        for (int i = 0; i < 10; i++) {
            alerts[i] = "{\n" +
                "  \"event_id\": \"" + UUID.randomUUID() + "\",\n" +
                "  \"timestamp\": \"2025-07-21T10:35:12.452Z\",\n" +
                "  \"event_type\": \"alerts.host.wazuh\",\n" +
                "  \"source_module\": \"WazuhConnector\",\n" +
                "  \"payload\": {\n" +
                "    \"host_id\": \"host-192.168.1." + (101 + i) + "\",\n" +
                "    \"alert_type\": \"ransomware_detection\",\n" +
                "    \"signature_id\": \"920102" + i + "\",\n" +
                "    \"signature\": \"Suspicious file encryption activity\",\n" +
                "    \"severity\": \"" + (i % 2 == 0 ? "high" : "medium") + "\",\n" +
                "    \"process\": \"C\\\\\\\\Users\\\\\\\\John\\\\\\\\AppData\\\\\\\\Local\\\\\\\\Temp\\\\\\\\malware" + i + ".exe\",\n" +
                "    \"file_path\": \"C\\\\\\\\Users\\\\\\\\John\\\\\\\\Documents\\\\\\\\encrypted_file" + i + ".docx\",\n" +
                "    \"matched_rule\": \"yara_ransomnote_heuristic\",\n" +
                "    \"source_ip\": \"192.168.1." + (101 + i) + "\",\n" +
                "    \"destination_ip\": \"10.0.0." + (5 + i) + "\",\n" +
                "    \"protocol\": \"TCP\",\n" +
                "    \"threat_score\": " + (80 + i) + "\n" +
                "  }\n" +
                "}";
        }

        WazuhAlertDao dao = new WazuhAlertDao();

        for (int i = 0; i < alerts.length; i++) {
            try {
                dao.insertAlert(alerts[i]); // DAO handles full row insert
                System.out.println("✅ Inserted alert " + (i + 1));
            } catch (Exception e) {
                System.err.println("❌ Failed to insert alert " + (i + 1));
                e.printStackTrace();
            }
        }

        System.out.println("🎯 All alerts processed.");
    }
}
