# TicketHub Notification Service (port 8085)

Listens to Kafka and e-mails the customer automatically. See `../RUN-GUIDE.md` for the full flow and tests.

Topics: `tickethub.booking.confirmed`, `tickethub.payment.failed`, `tickethub.booking.cancelled`
(bad messages go to `<topic>.DLT`).

Message required fields: `bookingId`, `recipientEmail`. Optional: `eventId`, `customerId`, `showId`, `seatIds`, `amount`,
`currency`, `showTime`, `paymentId`, `reason`, `customerName`, `eventName`, `venue`, `seatNumbers`.

Config: `src/main/resources/application-local.yml` (sender Gmail + App Password).
`notification.delivery-mode`: `SMTP` (real mail, default) or `LOG` (print only).

Extra APIs (history only - mail itself is never sent by an API):
`GET /api/notifications`, `/stats`, `/booking/{id}`, `POST /{id}/retry`, `POST /demo?email=` (test publisher).
