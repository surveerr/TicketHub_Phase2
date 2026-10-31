package com.tickethub.paymentservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.paymentservice.service.PaymentProviderService;
import com.tickethub.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Razorpay webhook receiver. The signature is computed over the EXACT raw body, so the body is
 * taken as a String and verified before it is parsed.
 */
@RestController
@RequestMapping("/api/payments/webhook")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentService paymentService;
    private final PaymentProviderService providerService;
    private final com.tickethub.paymentservice.repository.PaymentWebhookLogRepository webhookLogRepository;
    // Own mapper: it only reads the webhook JSON, so it does not depend on which Jackson
    // version Spring Boot auto-configures (avoids "No qualifying bean ObjectMapper" on Boot 4).
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentWebhookController(PaymentService paymentService,
                                    PaymentProviderService providerService,
                                    com.tickethub.paymentservice.repository.PaymentWebhookLogRepository webhookLogRepository) {
        this.paymentService = paymentService;
        this.providerService = providerService;
        this.webhookLogRepository = webhookLogRepository;
    }

    @PostMapping
    @Operation(summary = "Razorpay webhook (payment.captured, order.paid, payment.failed)",
            description = "Signature is verified against the raw body. Duplicate or late callbacks never corrupt payment state.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Processed (or ignored as duplicate/unknown)"),
                    @ApiResponse(responseCode = "400", description = "Body is not valid JSON"),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid signature")})
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "x-razorpay-event-id", required = false) String webhookEventId,
            @RequestBody String rawBody) {

        if (signature == null || !providerService.verifyWebhook(rawBody, signature)) {
            log.warn("Rejected webhook with missing/invalid signature");
            return ResponseEntity.status(401).build();
        }

        if (webhookEventId != null && !webhookEventId.isBlank()) {
            if (webhookLogRepository.existsByWebhookEventId(webhookEventId)) {
                log.info("Duplicate webhook event {} ignored (already processed)", webhookEventId);
                return ResponseEntity.ok().build();
            }
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText("");
            JsonNode payment = root.path("payload").path("payment").path("entity");
            JsonNode order = root.path("payload").path("order").path("entity");

            String orderId = payment.path("order_id").asText(null);
            if (orderId == null || orderId.isBlank()) {
                orderId = order.path("id").asText(null);
            }
            String paymentId = payment.path("id").asText(null);
            String reason = payment.path("error_description").asText(null);

            if (webhookEventId != null && !webhookEventId.isBlank()) {
                try {
                    webhookLogRepository.save(new com.tickethub.paymentservice.entity.PaymentWebhookLog(webhookEventId, orderId, event));
                } catch (Exception e) {
                    log.warn("Webhook log save race for event {}: {}", webhookEventId, e.getMessage());
                }
            }

            paymentService.handleProviderEvent(event, orderId, paymentId, reason);
            return ResponseEntity.ok().build();
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
