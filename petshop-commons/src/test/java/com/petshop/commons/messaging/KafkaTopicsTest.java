package com.petshop.commons.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicsTest {

    @Test
    void topicNamesAreKebabCase() {
        assertThat(KafkaTopics.BOOKING_SCHEDULED).isEqualTo("booking-scheduled");
        assertThat(KafkaTopics.BOOKING_COMPLETED).isEqualTo("booking-completed");
        assertThat(KafkaTopics.BOOKING_CANCELED).isEqualTo("booking-canceled");
        assertThat(KafkaTopics.ORDER_COMPLETED).isEqualTo("order-completed");
        assertThat(KafkaTopics.NOTIFICATION_COMMANDS).isEqualTo("notification-commands");
    }
}
