package com.yourorg.middleware;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.json.JSONObject;

public class Sender {
    public static void main(String[] args) throws Exception {
        File folder = new File("messages");
        WazuhAlertDao dao = new WazuhAlertDao();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            System.out.println("No JSON files found in messages/ folder.");
            return;
        }

        Arrays.sort(files);
        int count = 0;
        for (File file : files) {
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject json = new JSONObject(content);

            // Flatten payload fields into root level
            if (json.has("payload")) {
                JSONObject payload = json.getJSONObject("payload");
                for (String key : payload.keySet()) {
                    json.put(key, payload.get(key));
                }
                json.remove("payload");
            }

            try {
                dao.insertAlert(json);
                count++;
                System.out.println("Inserted: " + file.getName());
            } catch (Exception e) {
                System.out.println("Failed to insert: " + file.getName());
                e.printStackTrace();
            }
        }
        System.out.println("✅ Inserted " + count + " alerts into PostgreSQL.");
    }
}
