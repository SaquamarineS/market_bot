package ru.builder.bean.payment;

import java.util.Calendar;

public interface Payment {

    // Создание платежа
    void createPayment(String chatId);

    // Проверка статуса платежей
    void checkPaymentsStatus();

    // Создание автоматического платежа
    void createAutoPayment(ru.builder.model.payment.Payment payment, Calendar nextBillDate);

    // Повтор платежей с ошибкой
    void retryErrorPayments();

    // Повтор отмененных платежей
    void retryCanceledPayments();
}
