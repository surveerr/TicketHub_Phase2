# Paying for booking 2 (step by step)

0. In IntelliJ env vars for payment-service: `PAYMENT_PROVIDER=RAZORPAY`, `BOOKING_CLIENT=REST`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`.
   (Why your verify failed: payment PAY-3edea7e6... was created while the service ran in MOCK mode, so its order id is
   `order_mock_...`. Razorpay can't open or sign a mock order. This zip automatically retires such a leftover payment
   and creates a real Razorpay order the next time `POST /api/payments` is called for the booking.)
1. Book tickets -> booking 2 must be `PENDING_PAYMENT` and not expired (10 min hold). If expired, create a new booking and use its id.
2. Open `http://localhost:8083/checkout-test.html`, paste the same JWT you use in Postman, bookingId = 2, press **Pay now**.
3. The Razorpay popup opens. Test card `4111 1111 1111 1111`, any future date, any CVV (or UPI `success@razorpay`).
4. The page calls `/api/payments/verify` with the real signature -> `SUCCESS`; the Booking Service is told and the booking becomes `CONFIRMED`.
5. Check: `GET /api/v1/bookings/2` -> `CONFIRMED`, `GET /api/payments/booking/2` -> `SUCCESS`.

Never use Postman for `/verify` in Razorpay mode - the signature can only come from the popup.

---

# Opening the Razorpay payment popup

The popup is opened by the **browser** (checkout.js), never by the backend. Flow:

1. Frontend: `POST /api/payments` `{bookingId, idempotencyKey}` with `Authorization: Bearer <jwt>`
   -> response has `providerOrderId`, `checkoutKey`, `amount`, `currency`, `paymentReference`.
2. Frontend opens Razorpay Checkout with those values (needs real RAZORPAY provider, not MOCK).
3. On success Razorpay calls `handler` with `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`.
4. Frontend: `POST /api/payments/verify` with those three values + `paymentReference`.
   The signature is an HMAC that only Razorpay can produce - you can't type one in Postman.

Quick test: start the service and open  http://localhost:8083/checkout-test.html
(paste the JWT + bookingId, press "Pay now"). Test card: 4111 1111 1111 1111, any future expiry, any CVV,
OTP/“Success” on the test bank page. Test UPI: success@razorpay.

Snippet for your own frontend:

```html
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
<script>
async function pay(bookingId, jwt) {
  const h = {'Content-Type':'application/json','Authorization':'Bearer '+jwt};
  const p = await (await fetch('http://localhost:8083/api/payments',
      {method:'POST', headers:h, body:JSON.stringify({bookingId, idempotencyKey:'pay-'+bookingId})})).json();
  new Razorpay({
    key: p.checkoutKey, order_id: p.providerOrderId,
    amount: Math.round(p.amount*100), currency: p.currency, name: 'TicketHub',
    handler: async r => {
      const v = await fetch('http://localhost:8083/api/payments/verify', {method:'POST', headers:h, body:JSON.stringify({
        paymentReference: p.paymentReference, razorpayOrderId: r.razorpay_order_id,
        razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature})});
      console.log(await v.json());
    }
  }).open();
}
</script>
```
(If the frontend runs on another origin, add CORS to the payment-service SecurityConfig.)
