package com.tickethub.paymentservice.service;

public interface PaymentReconciliationService {

    /** @return number of payments that were updated */
    int reconcilePendingPayments();

    /** Scenario 8: Dual reconciliation report comparing payments against booking status */
    java.util.Map<String, Object> generateReconciliationAudit();
}
