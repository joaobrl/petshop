package com.petshop.commons.messaging;

/**
 * Nomes dos tópicos Kafka centralizados aqui para evitar string mágica
 * duplicada entre produtor e consumidor. Contém só os nomes — nenhum
 * schema/DTO de evento é compartilhado entre serviços.
 */
public final class KafkaTopics {

    public static final String BOOKING_SCHEDULED = "booking-scheduled";
    public static final String BOOKING_COMPLETED = "booking-completed";
    public static final String BOOKING_CANCELED = "booking-canceled";
    public static final String ORDER_COMPLETED = "order-completed";
    public static final String NOTIFICATION_COMMANDS = "notification-commands";

    private KafkaTopics() {
    }
}
