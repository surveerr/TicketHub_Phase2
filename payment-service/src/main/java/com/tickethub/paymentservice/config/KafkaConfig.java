package com.tickethub.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template,
                        (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)),
                new FixedBackOff(1000L, 2L));
        return handler;
    }

    @Bean
    public NewTopic paymentSucceededTopic(@Value("${kafka.topic.payment-succeeded:tickethub.payment.succeeded}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic(@Value("${kafka.topic.payment-failed:tickethub.payment.failed}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentRefundedTopic(@Value("${kafka.topic.payment-refunded:tickethub.payment.refunded}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingRejectedTopic(@Value("${kafka.topic.booking-rejected:tickethub.booking.rejected}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingCancelledTopic(@Value("${kafka.topic.booking-cancelled:tickethub.booking.cancelled}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededDlt(@Value("${kafka.topic.payment-succeeded:tickethub.payment.succeeded}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(1).replicas(1).build();
    }
}
