package ru.builder.db.payment;

import ru.builder.model.payment.CreatePayment;
import ru.builder.model.payment.Payment;
import ru.builder.model.payment.error.ErrorPayment;

import java.util.List;

public interface PaymentOperations {

    void createTable();

    void createErrorPaymentsTable();

    void addErrorPayment(String paymentMethodId, String request, boolean isAutoPay, String userId, String subscriptionType, String status);

    void addPayment(Payment payment, String userId, String messageId);

    void addAutoPayment(Payment payment, String userId);

    void setSucceededPaymentInfo(Payment payment);

    void setCanceledPaymentInfo(Payment payment);

    List<String> getPaymentIdsWithPendingStatus();

    Payment getPaymentByPaymentId(String id);

    List<CreatePayment> getErrorPaymentsByStatus(String status);

    String getErrorDateByPaymentMethodId(String paymentMethodId);

    ErrorPayment getErrorInfoByPaymentMethodId(String paymentMethodId);

    void setRetryCountForErrorAutoPayment(String paymentMethodId, int tryCount);

    void setNewStatusForErrorAutoPayment(String paymentMethodId, String newStatus, String oldStatus);

}
