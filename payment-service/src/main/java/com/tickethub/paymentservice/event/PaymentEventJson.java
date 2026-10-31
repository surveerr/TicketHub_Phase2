package com.tickethub.paymentservice.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class PaymentEventJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PaymentEventJson() {}

    public static String write(Object event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize event to JSON: " + e.getMessage(), e);
        }
    }

    public static <T> T parse(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Could not deserialize JSON to " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
