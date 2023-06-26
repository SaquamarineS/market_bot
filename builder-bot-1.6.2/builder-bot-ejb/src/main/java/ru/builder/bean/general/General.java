package ru.builder.bean.general;

import ru.builder.model.order.Order;

import java.util.Calendar;

public interface General {

    // Отправка сообщения о начале работы пользователю
    void sendStartMessage(String userId);

    // Отправка сообщения о потерянном пользователе
    void sendLostUserMessage(String userId);

    // Отправка главного меню пользователю
    void sendMainMenuMessage(String userId);

    // Получение новых заказов
    void getNewOrders();

    // Помещение заказов в очередь
    void putOrdersToQueue();

    // Отправка заказа пользователю
    void sendOrderToUser(Order order, String userId);

    // Получение списка доступных типов тяжелой техники для выбора
    void getChoiceHeavyMachineryTypes(String userId);

    // Отправка сообщения о выборе типа тяжелой техники
    void sendMessageAboutChoice(String userId, String heavyMachineryType);

    // Проверка подписки пользователя на определенный тип тяжелой техники
    boolean checkSubscriptionForUserId(String userId, String heavyMachineryType);

    /**  
     * Отображение номера телефона пользователю
     * Нет таймера, т.к. номер телефона отображается сразу. Как только пользователь нажимает на кнопку "Показать номер телефона", 
     * то сразу отображается номер телефона
    */
    // До
      // void showPhoneNumber(String userId, Integer messageId, String text);
    // После
    void showPhoneNumber(String userId, Integer messageId, String text, Date orderCreationTime);


    // Отправка запроса на электронную почту
    void sendRequestEmail(String chatId);

    // Сохранение адреса электронной почты
    void saveEmail(String chatId, String email);

    // Добавление информации о подписчике
    void addSubscriberInfo(String chatId, String heavyMachineryType);

    // Получение всех активных и пробных подписок пользователя
    void getAllActiveAndTrialSubscriptionsForUser(String chatId);

    // Получение справочной информации
    void getHelpInfo(String chatId);

    // Получение информации о ценах
    void getPriceInfo(String chatId);

    // Отмена подписки
    void unsubscribe(String chatId, String equipmentType, Integer messageId);

    // Отправка запроса о проблеме
    void requestProblem(String chatId);

    // Проверка наличия активной подписки у пользователя
    boolean checkActiveSubscription(String chatId, String equipmentType);

    // Проверка наличия пробной подписки у пользователя
    boolean checkTrialSubscription(String chatId, String equipmentType);

    // Проверка наличия подписки с автоматическим платежом у пользователя
    boolean checkAutoPaySubscription(String chatId, String equipmentType);

    // Отправка сообщения о наличии активной подписки у пользователя
    void sendAlreadyHaveSubscription(String chatId, String equipmentType);

    // Отправка сообщения о наличии автоматического платежа у пользователя
    void sendHaveAutoPayment(String chatId, String equipmentType);

    // Отправка сообщения о отсутствии пробной подписки у пользователя
    void sendDontHaveTrialSubscription(String chatId, String equipmentType);

    // Отправка сообщения о непредвиденной ошибке
    void sayUnexpectedError(String userId, String message);

    // Отправка сообщения об ошибке автоматического платежа
    void sayAutoPaymentError(String userId, String message);

    // Отправка сообщения об успешном платеже
    void sendMessageAboutSuccessfulPayment(String userId, String equipmentType);

    // Отправка сообщения об отмене платежа
    void sendMessageAboutCanceledPayment(String userId, String reasonValue);

    // Отправка предупреждения о скором истечении срока действия подписки
    void sendWarningMessageAboutSubscriptionWillBeExpired(String chatId, String paymentId, Calendar warningTime);

    // Отправка сообщения об отмене автоматического платежа
    void sendMessageAboutCanceledAutoPayment(String chatId, String reasonValue);

    // Отправка сообщения об успешном автоматическом платеже
    void sendMessageAboutSuccessfullyAutoPayment(String chatId, String equipmentType);

    // Приветствие после отмены подписки
    void sayHelloAfterUnsubscribe(String chatId, String equipmentType);

    // Отправка сообщения об ошибке автоматического платежа
    void sendMessageAboutAutoPaymentError(String chatId, String equipmentType);

    // Запрос на обратную связь по отписке
    void sendRequestFeedbackAboutUnsubscribe(String chatId);

    // Запрос на обратную связь
    void feedbackRequest(String chatId);

    // Отмена запроса на обратную связь
    void cancelFeedback(String chatId);

    // Отправка благодарности пользователю за его отзыв
    void sendThanksToUserForHisFeedback(String chatId);

    // Удаление неактивных пользователей с пробным статусом администратором
    void deleteInactiveUsersByAdmin(String userId);

    // Удаление неактивных пользователей с пробным статусом по расписанию
    void deleteInactiveUsersBySchedule();
}
