package com.systemclient;

import com.encryptutil.*;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

public class systemClient {
    private static final String EXCHANGE_NAME = "secure_exchange";
    private static final String SECRET_KEY = "12345678901234567890123456789012"; // shared key

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Client <clientId>");
            System.exit(1);
        }
        String clientId = args[0];

        // Connect to RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(15672);
        factory.setUsername("guest");
        factory.setPassword("guest");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT);

        // Queue for this client
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        // Listener
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String encrypted = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                String decryptedJson = encryptUtil.decrypt(encrypted, SECRET_KEY);
                System.out.println("\n📥 Received JSON:\n" + decryptedJson);
            } catch (Exception e) {
                System.err.println("❌ Failed to decrypt: " + e.getMessage());
            }
        };
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });

        try (// Sending loop
        Scanner scanner = new Scanner(System.in)) {
            System.out.println("Client [" + clientId + "] ready. Type JSON messages:");
            while (true) {
                String json = scanner.nextLine();

                // Wrap JSON with event metadata
                String event = String.format("""
                {
                  "event_id": "%s",
                  "sender": "%s",
                  "payload": %s
                }
                """, UUID.randomUUID(), clientId, json);

                String encrypted = encryptUtil.encrypt(event, SECRET_KEY);
                channel.basicPublish(EXCHANGE_NAME, "", null, encrypted.getBytes(StandardCharsets.UTF_8));
                System.out.println("📤 Sent encrypted message.");
            }
        }
    }
}
