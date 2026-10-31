package com.tickethub.paymentservice.service.impl;

import com.tickethub.paymentservice.config.ProviderConfig;
import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.exception.ProviderUnavailableException;
import com.tickethub.paymentservice.provider.PaymentProviderClient;
import com.tickethub.paymentservice.provider.PaymentProviderClient.OrderStatusResult;
import com.tickethub.paymentservice.provider.ProviderException;
import com.tickethub.paymentservice.service.PaymentProviderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Every remote provider call runs with an explicit timeout (payment.provider.timeout-ms) and goes through the
 * "paymentProvider" circuit breaker. A timeout is reported as a ProviderException, so the breaker counts it as a
 * failure and a slow provider ends in a fast, controlled "unavailable" error instead of a hung request.
 * Nothing here is retried automatically: order creation and refunds are not idempotent.
 */
@Service
public class PaymentProviderServiceImpl implements PaymentProviderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderServiceImpl.class);
    private static final String CB = "paymentProvider";

    private final PaymentProviderClient client;
    private final long timeoutMs;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public PaymentProviderServiceImpl(PaymentProviderClient client, ProviderConfig config) {
        this.client = client;
        this.timeoutMs = config.getTimeoutMs();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "createOrderFallback")
    public String createOrder(Payment payment) {
        return callWithTimeout("createOrder", () ->
                client.createOrder(payment.getPaymentReference(), payment.getAmount(), payment.getCurrency()));
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        return client.verifyPayment(orderId, paymentId, signature);      // local signature check, no remote call
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signature) {
        return client.verifyWebhook(rawBody, signature);                 // local signature check, no remote call
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "checkOrderStatusFallback")
    public OrderStatusResult checkOrderStatus(String orderId) {
        return callWithTimeout("fetchOrderStatus", () -> client.fetchOrderStatus(orderId));
    }

    @Override
    @CircuitBreaker(name = CB, fallbackMethod = "refundFallback")
    public String refund(Payment payment) {
        return callWithTimeout("refund", () -> client.refund(payment.getProviderTransactionId(), payment.getAmount()));
    }

    @Override
    public String getPublicKey() {
        return client.getPublicKey();
    }

    private <T> T callWithTimeout(String operation, Supplier<T> call) {
        Callable<T> task = call::get;
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProviderException("Provider " + operation + " timed out after " + timeoutMs + " ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new ProviderException("Provider " + operation + " failed: " + cause, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Provider " + operation + " was interrupted", e);
        }
    }

    // ---- fallbacks (must match the method signature plus a trailing Throwable) ----

    public String createOrderFallback(Payment payment, Throwable t) {
        log.error("Could not create provider order for {} (bookingId={}): {}", payment.getPaymentReference(),
                payment.getBookingId(), t.toString());
        throw new ProviderUnavailableException("Payment provider unavailable, please try again shortly");
    }

    public OrderStatusResult checkOrderStatusFallback(String orderId, Throwable t) {
        log.error("Provider status check failed for order {}: {}", orderId, t.toString());
        return new OrderStatusResult("PENDING", null, "Provider unavailable, will retry");
    }

    public String refundFallback(Payment payment, Throwable t) {
        log.error("Provider refund failed for {} (bookingId={}): {}", payment.getPaymentReference(),
                payment.getBookingId(), t.toString());
        throw new ProviderUnavailableException("Payment provider unavailable, refund was not processed");
    }
}
