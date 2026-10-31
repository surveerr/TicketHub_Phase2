# TicketHub Payment Service

## Security
Customer endpoints require a Bearer JWT. The customer ID is taken from the JWT subject; client-supplied user IDs are not accepted.

## Booking integration
When `BOOKING_CLIENT=REST` (the reviewed default), Payment Service reads Booking Service through Eureka using the internal service JWT:
- GET `/internal/v1/bookings/{id}`
- POST `/internal/v1/bookings/{id}/payment-result`

The Booking Service remains the source of truth for owner, amount, currency and payable status.

## Provider
`PAYMENT_PROVIDER=MOCK` is the default for local testing. Razorpay can be enabled with the existing provider configuration.
