package com.tickethub.notification.config;

import com.tickethub.notification.exception.InvalidEventException;
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

    /**
     * Bad message  -> straight to "<topic>.DLT" (no retry).
     * Other errors -> 3 tries, 1 second apart, then "<topic>.DLT".
     * The consumer never gets stuck on one poison message.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template,
                        // partition -1 = let Kafka choose (the DLT has 1 partition, the source topic has 3)
                        (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)),
                new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    // Topics are created on startup if they do not exist yet.
    @Bean
    public NewTopic bookingConfirmedTopic(@Value("${kafka.topic.booking-confirmed}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingCancelledTopic(@Value("${kafka.topic.booking-cancelled}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic(@Value("${kafka.topic.payment-failed}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingConfirmedDlt(@Value("${kafka.topic.booking-confirmed}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bookingCancelledDlt(@Value("${kafka.topic.booking-cancelled}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedDlt(@Value("${kafka.topic.payment-failed}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(1).replicas(1).build();
    }
}
