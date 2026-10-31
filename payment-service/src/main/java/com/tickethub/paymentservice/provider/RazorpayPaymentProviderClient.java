package com.tickethub.paymentservice.provider;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import com.tickethub.paymentservice.config.ProviderConfig;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Real Razorpay implementation. Active when payment.provider.name=RAZORPAY.
 * Note: com.razorpay.Payment is referenced by its full name to avoid clashing with our Payment entity.
 */
@Component
@ConditionalOnProperty(prefix = "payment.provider", name = "name", havingValue = "RAZORPAY")
public class RazorpayPaymentProviderClient implements PaymentProviderClient {

    private final RazorpayClient razorpay;
    private final ProviderConfig config;

    public RazorpayPaymentProviderClient(ProviderConfig config) {
        this.config = config;
        if (isBlank(config.getKeyId()) || isBlank(config.getKeySecret())) {
            throw new IllegalStateException(
                    "RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set when PAYMENT_PROVIDER=RAZORPAY");
        }
        try {
            this.razorpay = new RazorpayClient(config.getKeyId(), config.getKeySecret());
        } catch (RazorpayException e) {
            throw new IllegalStateException("Could not initialise Razorpay client: " + e.getMessage(), e);
        }
    }

    @Override
    public String createOrder(String paymentReference, BigDecimal amount, String currency) {
        try {
            JSONObject notes = new JSONObject();
            notes.put("paymentReference", paymentReference);

            JSONObject request = new JSONObject();
            request.put("amount", toSubunits(amount));
            request.put("currency", currency);
            request.put("receipt", paymentReference);       // Razorpay allows max 40 chars
            request.put("notes", notes);

            Order order = razorpay.orders.create(request);
            String orderId = order.get("id");
            return orderId;
        } catch (RazorpayException e) {
            throw new ProviderException("Razorpay order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(attributes, config.getKeySecret());
        } catch (RazorpayException e) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signature) {
        if (isBlank(signature) || isBlank(config.getWebhookSecret())) {
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(rawBody, signature, config.getWebhookSecret());
        } catch (RazorpayException e) {
            return false;
        }
    }

    @Override
    public OrderStatusResult fetchOrderStatus(String orderId) {
        try {
            List<com.razorpay.Payment> payments = razorpay.orders.fetchPayments(orderId);
            for (com.razorpay.Payment p : payments) {
                String status = p.get("status");
                if ("captured".equals(status)) {
                    String paymentId = p.get("id");
                    return new OrderStatusResult("PAID", paymentId, "captured");
                }
            }
            return new OrderStatusResult("PENDING", null, "No captured payment for this order");
        } catch (RazorpayException e) {
            throw new ProviderException("Razorpay order lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String refund(String paymentId, BigDecimal amount) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", toSubunits(amount));
            Refund refund = razorpay.payments.refund(paymentId, request);
            String refundId = refund.get("id");
            return refundId;
        } catch (RazorpayException e) {
            throw new ProviderException("Razorpay refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPublicKey() {
        return config.getKeyId();
    }

    /** Razorpay amounts are integers in the smallest currency unit (paise for INR). */
    private static long toSubunits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
