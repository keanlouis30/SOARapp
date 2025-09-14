package com.nis1.thesis.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fatboyindustrial.gsonjavatime.Converters;
import com.nis1.thesis.sdk.CoreSystemApi;
import com.nis1.thesis.sdk.Event;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;



/**
 * Basic implementation of CoreSystemApi that publishes events to RabbitMQ.
 */
public class CoreSystemApiImpl implements CoreSystemApi {
    private final Channel channel;
    private final Gson gson;

    public CoreSystemApiImpl() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("user");
        factory.setPassword("password");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();

        channel.exchangeDeclare("security_events", "topic", true);

        this.gson = Converters.registerAll(new GsonBuilder()).create();
    }

    @Override
    public void publishEvent(Event<?> event) {
        try {
            String json = gson.toJson(event);
            channel.basicPublish("security_events", event.getType(), null, json.getBytes());
            System.out.println("Published event: " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
