package com.yourorg.middleware;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.json.JSONObject;

public class QueryDemo {
    private static final String QUEUE_NAME = "alerts_queue";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java -cp ... QueryDemo <severity>");
            return;
        }

        String severity = args[0];
        WazuhAlertDao dao = new WazuhAlertDao();
        List<JSONObject> alerts = dao.findBySeverity(severity);

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); 
        factory.setPort(5672);        
        factory.setUsername("user"); 
        factory.setPassword("password"); 

        Connection connection = null;
        Channel channel = null;

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            System.out.println("✅ Connected to RabbitMQ queue: " + QUEUE_NAME);
        } catch (Exception e) {
            System.out.println("⚠ RabbitMQ not available, continuing with file export only.");
        }

        int count = 0;
        int fileIndex = 1;
        for (JSONObject alert : alerts) {
            // Write JSON to file
            try (FileWriter writer = new FileWriter(new File(outputDir, "alert_" + fileIndex + ".json"))) {
                writer.write(alert.toString(4)); // pretty-print with 4 spaces
            } catch (Exception e) {
                System.out.println("Failed to write alert_" + fileIndex + ".json");
                e.printStackTrace();
            }

            // Send JSON to RabbitMQ if available
            if (channel != null) {
                try {
                    channel.basicPublish("", QUEUE_NAME, null, alert.toString().getBytes("UTF-8"));
                    System.out.println("Sent alert_" + fileIndex + " to RabbitMQ.");
                } catch (Exception e) {
                    System.out.println("Failed to send alert_" + fileIndex + " to RabbitMQ.");
                    e.printStackTrace();
                }
            }

            fileIndex++;
            count++;
        }

        // Cleanup RabbitMQ connection
        if (channel != null) channel.close();
        if (connection != null) connection.close();

        System.out.println("✅ Exported " + count + " alerts with severity = " + severity + " to output/");
    }
}
