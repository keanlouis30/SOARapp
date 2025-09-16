package com.nis1.thesis.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fatboyindustrial.gsonjavatime.Converters;
import com.nis1.thesis.sdk.CoreSystemApi;
import com.nis1.thesis.sdk.Event;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Enhanced implementation of CoreSystemApi with subscription support and connection resilience.
 * <p>
 * This implementation provides robust communication capabilities with RabbitMQ message broker,
 * including automatic reconnection, event queuing during connection outages, and fault-tolerant
 * subscription management. It serves as the central communication hub for all pluggable modules
 * in the SOAR framework.
 * </p>
 * <p>
 * Key features include:
 * <ul>
 *   <li>Automatic connection recovery and reconnection logic</li>
 *   <li>Event queuing for offline message delivery</li>
 *   <li>Subscription persistence across reconnections</li>
 *   <li>Concurrent event processing and publishing</li>
 *   <li>Comprehensive error handling and logging</li>
 * </ul>
 *
 * @author NIS1
 * @version 1.0
 * @since September 10, 2025
 */
public class CoreSystemApiImpl implements CoreSystemApi {
    private static final String EXCHANGE_NAME = "security_events";
    private static final int RECONNECT_DELAY_SECONDS = 2;
    private static final int MAX_QUEUE_SIZE = 1000;
    
    private volatile Connection connection;
    private volatile Channel publishChannel;
    private volatile Channel subscribeChannel;
    private final Gson gson;
    private final ConnectionFactory factory;
    private final Map<String, Consumer<Event<?>>> subscriptions = new ConcurrentHashMap<>();
    private final BlockingQueue<Event<?>> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean isConnected = false;

    /**
     * Constructs and initializes the CoreSystemApi implementation.
     * <p>
     * Sets up RabbitMQ connection factory with automatic recovery, establishes initial
     * connection to the message broker, and starts background services for event processing
     * and connection management.
     * </p>
     * 
     * @throws Exception if initial connection to RabbitMQ fails or configuration is invalid
     */
    public CoreSystemApiImpl() throws Exception {
        this.gson = Converters.registerAll(new GsonBuilder()).create();
        
        // Configure connection factory
        this.factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("user");
        factory.setPassword("password");
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        
        // Initial connection
        connect();
        
        // Start queue processor for offline events
        startQueueProcessor();
    }

    /**
     * Establishes connection to RabbitMQ broker and sets up message channels.
     * <p>
     * Creates new connection and channels for publishing and subscribing, declares
     * the topic exchange, and re-establishes any existing subscriptions after
     * a successful connection.
     * </p>
     */
    private void connect() {
        try {
            this.connection = factory.newConnection();
            this.publishChannel = connection.createChannel();
            this.subscribeChannel = connection.createChannel();
            
            // Declare exchange
            publishChannel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            subscribeChannel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
            
            this.isConnected = true;
            System.out.println("[CoreSystemApi] Connected to RabbitMQ");
            
            // Re-establish subscriptions
            reestablishSubscriptions();
            
        } catch (Exception e) {
            System.err.println("[CoreSystemApi] Failed to connect: " + e.getMessage());
            this.isConnected = false;
            scheduleReconnect();
        }
    }
    
    /**
     * Schedules an automatic reconnection attempt after a configured delay.
     * <p>
     * Uses the scheduler service to retry connection establishment, providing
     * resilience against temporary network or broker outages.
     * </p>
     */
    private void scheduleReconnect() {
        scheduler.schedule(() -> {
            System.out.println("[CoreSystemApi] Attempting to reconnect...");
            connect();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Starts the background queue processor for handling offline events.
     * <p>
     * This processor continuously monitors the event queue and attempts to publish
     * any queued events when the connection is available. This ensures that events
     * published during connection outages are not lost.
     * </p>
     */
    private void startQueueProcessor() {
        scheduler.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (isConnected && !eventQueue.isEmpty()) {
                        Event<?> event = eventQueue.poll();
                        if (event != null) {
                            publishEventDirectly(event);
                        }
                    }
                    Thread.sleep(100); // Brief pause
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[CoreSystemApi] Queue processor error: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Re-establishes all existing subscriptions after a connection recovery.
     * <p>
     * Iterates through all stored subscriptions and attempts to recreate them
     * on the new connection. Failed subscriptions are logged but don't prevent
     * other subscriptions from being established.
     * </p>
     */
    private void reestablishSubscriptions() {
        for (Map.Entry<String, Consumer<Event<?>>> entry : subscriptions.entrySet()) {
            try {
                setupSubscription(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                System.err.println("[CoreSystemApi] Failed to reestablish subscription for: " + entry.getKey());
            }
        }
    }

    @Override
    public void publishEvent(Event<?> event) {
        if (isConnected) {
            publishEventDirectly(event);
        } else {
            // Queue for later delivery
            if (!eventQueue.offer(event)) {
                System.err.println("[CoreSystemApi] Event queue full, dropping event: " + event.getType());
            } else {
                System.out.println("[CoreSystemApi] Queued event for later delivery: " + event.getType());
            }
        }
    }
    
    /**
     * Publishes an event directly to the RabbitMQ exchange.
     * <p>
     * Serializes the event to JSON and publishes it to the topic exchange using
     * the event type as the routing key. If publishing fails, the connection is
     * marked as disconnected, reconnection is scheduled, and the event is re-queued.
     * </p>
     * 
     * @param event The event to publish directly to the message broker
     */
    private void publishEventDirectly(Event<?> event) {
        try {
            String json = gson.toJson(event);
            publishChannel.basicPublish(EXCHANGE_NAME, event.getType(), null, json.getBytes());
            System.out.println("[CoreSystemApi] Published event: " + event.getType());
        } catch (Exception e) {
            System.err.println("[CoreSystemApi] Failed to publish event: " + e.getMessage());
            isConnected = false;
            scheduleReconnect();
            // Re-queue the event
            eventQueue.offer(event);
        }
    }

    @Override
    public void subscribeToEvent(String eventType, Consumer<Event<?>> listener) {
        // Store subscription for reconnection scenarios
        subscriptions.put(eventType, listener);
        
        if (isConnected) {
            try {
                setupSubscription(eventType, listener);
            } catch (Exception e) {
                System.err.println("[CoreSystemApi] Failed to setup subscription: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sets up a subscription for a specific event type or pattern.
     * <p>
     * Creates a temporary queue, binds it to the exchange with the specified routing
     * pattern, and establishes a consumer that deserializes incoming messages and
     * delivers them to the provided listener function.
     * </p>
     * 
     * @param eventType The event type pattern to subscribe to (supports AMQP wildcards)
     * @param listener The callback function to handle received events
     * @throws IOException if queue creation or binding fails
     */
    private void setupSubscription(String eventType, Consumer<Event<?>> listener) throws IOException {
        // Create a unique queue for this subscription
        String queueName = subscribeChannel.queueDeclare("", false, false, true, null).getQueue();
        
        // Bind queue to exchange with routing pattern
        subscribeChannel.queueBind(queueName, EXCHANGE_NAME, eventType);
        
        // Create consumer
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), "UTF-8");
                Event<?> event = gson.fromJson(message, Event.class);
                listener.accept(event);
            } catch (Exception e) {
                System.err.println("[CoreSystemApi] Error processing event: " + e.getMessage());
            }
        };
        
        CancelCallback cancelCallback = consumerTag -> {
            System.out.println("[CoreSystemApi] Consumer cancelled: " + consumerTag);
        };
        
        subscribeChannel.basicConsume(queueName, true, deliverCallback, cancelCallback);
        System.out.println("[CoreSystemApi] Subscribed to: " + eventType);
    }
    
    /**
     * Gracefully shuts down the CoreSystemApi implementation.
     * <p>
     * Stops the scheduler service, closes all RabbitMQ channels and connections,
     * and performs cleanup of allocated resources. Any errors during shutdown
     * are logged but don't prevent the shutdown process from completing.
     * </p>
     */
    public void shutdown() {
        try {
            scheduler.shutdown();
            if (publishChannel != null && publishChannel.isOpen()) {
                publishChannel.close();
            }
            if (subscribeChannel != null && subscribeChannel.isOpen()) {
                subscribeChannel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            System.out.println("[CoreSystemApi] Shutdown complete");
        } catch (Exception e) {
            System.err.println("[CoreSystemApi] Error during shutdown: " + e.getMessage());
        }
    }
}
