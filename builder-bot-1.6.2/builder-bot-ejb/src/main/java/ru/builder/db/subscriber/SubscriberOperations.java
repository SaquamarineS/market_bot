package ru.builder.db.subscriber;

import ru.builder.model.Equipment;
import ru.builder.model.subscr.Subscriber;

import java.util.List;

public interface SubscriberOperations {

    void createTable();

    void addSubscriber(Subscriber subscriber);

    void updateSubscriber(Subscriber subscriber);

    void addSubscriberAfterAutoPayment(Subscriber subscriber, String date);

    void updateSubscriptionInfo(String userId, String paymentId, String date, boolean isExpired);

    void updateSubscriptionInfo(String userId, String paymentId);

    List<Subscriber> getAllActiveSubscribers();

    Subscriber getSubscriberByPaymentId(String paymentId);

    String getEquipmentTypeByPaymentId(String paymentId);

    Subscriber getSubscriberByIdAndEquipmentTypeAndActiveOrAutoPayStatus(String userId, String equipmentType);

    Subscriber getActiveOrTrialSubscriberByIdAndEquipmentType(String userId, String equipmentType);

    boolean checkIsActiveSubscriber(String userId, String equipmentType);

    void changeConnectionCount(String userId, String equipmentType, int connectionCount);

    void cancelPaidSubscription(String userId, String equipmentType);

    void cancelTrialSubscription(String userId, String equipmentType, boolean isBlocked);

    boolean checkActiveSubscription(String userId, String equipmentType);

    boolean checkTrialSubscription(String userId, String equipmentType);

    boolean checkAutoPaySubscription(String userId, String equipmentType);

    boolean checkUnsubscribeUser(String userId);

    boolean checkSubscriberIsAlreadyExist(String userId);

    List<Subscriber> getAllActiveAndTrialSubscriptionsForUser(String userId);

    List<Subscriber> getAllActiveWithoutAutoPaySubscriptionsForUser(String userId);

    void deleteInactiveUser(String userId, String equipmentType);

    // === Restore subscription after restart the bot ===
    List<Subscriber> getPauseSubscriptions(String userId);

    void restoreTrialStatusForSubscription(Subscriber subscriber);

    // === For deleting inactive users with Trial status ===
    List<Subscriber> getOldInactiveUsers();

    void cancelInactiveOLDTrialSubscription(String userId, String equipmentType);

    List<Subscriber> getInactiveUsersForMoreThanFourMonths(String beforeDate);

    void cancelInactiveTrialSubscription(String userId, String equipmentType, String beforeDate);

    // === Statistic ===

    // Сколько всего триальных/платных подписок на текущий момент
    long getCountOfSubscriptionsForEachUser(String status);

    // Сколько триальных/платных подписок по конкретной спецтехнике на текущий момент
    List<Equipment> getCountOfSubscriptionsForEachEquipment(String status);

    // Сколько платных подписок и какая длительность подписки по конкретной спецтехнике на текущий момент
    List<Equipment> getCountAndDurationOfPaidSubscriptionsForEachEquipment();

    // Общее кол-во пользователей
    long getCountOfBotUsers();
}