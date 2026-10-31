package com.tickethub.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        return new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)),
                new FixedBackOff(1000L, 2L));
    }

    @Bean
    public NewTopic bookingConfirmedTopic(@Value("${kafka.topic.booking-confirmed:tickethub.booking.confirmed}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    @Bean
    public NewTopic paymentFailedTopic(@Value("${kafka.topic.payment-failed:tickethub.payment.failed}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    @Bean
    public NewTopic bookingCancelledTopic(@Value("${kafka.topic.booking-cancelled:tickethub.booking.cancelled}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    @Bean
    public NewTopic paymentSucceededTopic(@Value("${kafka.topic.payment-succeeded:tickethub.payment.succeeded}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    @Bean
    public NewTopic bookingRejectedTopic(@Value("${kafka.topic.booking-rejected:tickethub.booking.rejected}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }

    @Bean
    public NewTopic paymentRefundedTopic(@Value("${kafka.topic.payment-refunded:tickethub.payment.refunded}") String name) {
        return new NewTopic(name, 3, (short) 1);
    }
}
