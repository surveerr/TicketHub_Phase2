package com.tickethub.notification.service;

import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds subject + plain text + HTML for each notification type. */
@Service
public class EmailTemplateService {

    public EmailContent build(NotificationType type, BookingConfirmedEvent e) {
        String subject;
        String heading;
        String intro;
        String color;

        switch (type) {
            case BOOKING_CANCELLED -> {
                subject = "TicketHub - Booking #" + e.bookingId() + " cancelled";
                heading = "Booking Cancelled";
                intro = "Your booking has been cancelled." + reasonSentence(e);
                color = "#d97706";
            }
            case PAYMENT_FAILED -> {
                subject = "TicketHub - Payment failed for booking #" + e.bookingId();
                heading = "Payment Failed";
                intro = "We could not process your payment, so the booking was not confirmed." + reasonSentence(e);
                color = "#dc2626";
            }
            default -> {
                subject = "TicketHub - Booking #" + e.bookingId() + " confirmed";
                heading = "Booking Confirmed";
                intro = "Thank you for booking with TicketHub. Your tickets are confirmed!";
                color = "#16a34a";
            }
        }

        String name = isBlank(e.customerName()) ? "there" : e.customerName();
        Map<String, String> rows = details(e);

        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(name).append(",\n\n").append(intro).append("\n\n");
        rows.forEach((k, v) -> text.append(k).append(": ").append(v).append("\n"));
        text.append("\nTicketHub Team");

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:auto;")
                .append("border:1px solid #e5e7eb;border-radius:8px;overflow:hidden\">")
                .append("<div style=\"background:").append(color).append(";color:#fff;padding:18px 24px\">")
                .append("<h2 style=\"margin:0\">").append(esc(heading)).append("</h2></div>")
                .append("<div style=\"padding:24px\"><p>Hi ").append(esc(name)).append(",</p><p>")
                .append(esc(intro)).append("</p>")
                .append("<table style=\"width:100%;border-collapse:collapse\">");
        rows.forEach((k, v) -> html.append("<tr><td style=\"padding:8px;border-bottom:1px solid #f3f4f6;")
                .append("color:#6b7280;width:40%\">").append(esc(k))
                .append("</td><td style=\"padding:8px;border-bottom:1px solid #f3f4f6;font-weight:bold\">")
                .append(esc(v)).append("</td></tr>"));
        html.append("</table><p style=\"color:#6b7280;font-size:12px;margin-top:24px\">")
                .append("This is an automated message from TicketHub.</p></div></div>");

        return new EmailContent(subject, text.toString(), html.toString());
    }

    private Map<String, String> details(BookingConfirmedEvent e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Booking ID", "#" + e.bookingId());
        if (!isBlank(e.eventName())) m.put("Event", e.eventName());
        if (!isBlank(e.venue())) m.put("Venue", e.venue());
        if (!isBlank(e.showTime())) m.put("Show time", e.showTime());
        else if (e.showId() != null) m.put("Show ID", String.valueOf(e.showId()));

        List<String> seats = new ArrayList<>();
        if (e.seatNumbers() != null && !e.seatNumbers().isEmpty()) {
            seats.addAll(e.seatNumbers());
        } else if (e.seatIds() != null) {
            e.seatIds().forEach(id -> seats.add("#" + id));
        }
        if (!seats.isEmpty()) m.put("Seats", String.join(", ", seats));

        if (e.amount() != null) {
            String cur = isBlank(e.currency()) ? "" : e.currency() + " ";
            m.put("Amount", cur + e.amount().toPlainString());
        }
        if (!isBlank(e.paymentId())) m.put("Payment ID", e.paymentId());
        return m;
    }

    private String reasonSentence(BookingConfirmedEvent e) {
        return isBlank(e.reason()) ? "" : " Reason: " + e.reason() + ".";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }
}
