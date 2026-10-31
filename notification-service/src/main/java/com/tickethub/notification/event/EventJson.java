package com.tickethub.notification.event;

import com.tickethub.notification.exception.InvalidEventException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Small JSON helper (Jackson 3, shipped with Spring Boot 4). */
public final class EventJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private EventJson() {
    }

    public static BookingConfirmedEvent parse(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidEventException("Kafka message is empty");
        }
        try {
            BookingConfirmedEvent event = MAPPER.readValue(json, BookingConfirmedEvent.class);
            if (event == null) {
                throw new InvalidEventException("Kafka message is null");
            }
            return event;
        } catch (InvalidEventException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidEventException("Kafka message is not valid JSON: " + e.getMessage(), e);
        }
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
