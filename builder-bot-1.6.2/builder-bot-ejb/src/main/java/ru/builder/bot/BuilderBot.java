package ru.builder.bot;

import com.google.gson.Gson;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.builder.bean.admin.Admin;
import ru.builder.bean.general.General;
import ru.builder.bean.payment.Payment;
import ru.builder.bean.statistic.Statistic;
import ru.builder.redis.RedisEntity;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Для работы с датой и временем
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


public class BuilderBot extends TelegramLongPollingBot {

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final Pattern PATTERN = Pattern.compile("^(week|month|year|test)_\\d{1,2}$");
    private final String botName;
    private final String botToken;
    private General general;
    private Payment payment;
    private Admin admin;
    private Statistic statistic;
    private RedisEntity redisEntity;
    // Переменные для хранения данных о заказе
    private LocalDateTime orderCreatedTime; // Переменная для хранения момента создания заказа
    private static final int EXPIRATION_HOURS = 6; // Количество часов, через которое заказ становится неактуальным


    public BuilderBot() {
        this.botName = System.getenv("builder_botName");
        this.botToken = System.getenv("builder_botToken");
    }

    @Inject
    public BuilderBot(General general, Payment payment, Admin admin, Statistic statistic, RedisEntity redisEntity) {
        this.redisEntity = redisEntity;
        this.botName = System.getenv("builder_botName");
        this.botToken = System.getenv("builder_botToken");
        this.general = general;
        this.payment = payment;
        this.admin = admin;
        this.statistic = statistic;
    }

    // Очищаем все данные пользователя
    // (при запуске бота, при нажатии на кнопку "Главное меню")
    // (при нажатии на кнопку "Закрыть диалог")
    // (при нажатии на кнопку "Написать нам")
    private void cleanUp(String userId) {
        redisEntity.deleteElement(userId.concat("_help"));
        redisEntity.deleteElement(userId.concat("_from_chatId"));
        redisEntity.deleteElement(userId.concat("_from_messageId"));
        redisEntity.deleteElement(userId.concat("_from_messageText"));
        redisEntity.deleteElement(userId.concat("_from_messageDate"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthor"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorId"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorUsername"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorFirstName"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorLastName"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorLanguageCode"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorIsBot"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorCanJoinGroups"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorCanReadAllGroupMessages"));
        redisEntity.deleteElement(userId.concat("_from_messageAuthorSupportsInlineQueries"))
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update != null) {

                Message message = update.getMessage();

                // Обрабатываем ответ от админа из канала с тех. поддержкой
                if (update.hasChannelPost() &&
                        update.getChannelPost() != null
                        && update.getChannelPost().getReplyToMessage() != null) {
                    admin.sendMessageFromChannel(update.getChannelPost());
                    return;
                }

                if (message != null) {

                    String userId = String.valueOf(message.getFrom().getId());

                    boolean isAdmin = redisEntity.getElement(userId.concat("_isAdmin")) != null
                            && !redisEntity.getElement(userId.concat("_isAdmin")).isEmpty()
                            && redisEntity.getElement(userId.concat("_isAdmin")).equals("true");

                    if (message.getText() != null) {
                        if (message.getText().equals("/start")) {
                            cleanUp(userId);
                            // Удаляем флажок с указанием id канала, куда должен прилететь фидбек от пользователя
                            redisEntity.deleteElement(userId.concat("_from_chatId"));
                            if (!general.checkSubscriberIsAlreadyExist(userId))
                                // Сохранем ID пользователя для статистики
                                redisEntity.pushElement("new_users", userId);
                            general.sendStartMessage(userId);
                        } else if (message.getText().equals("/start refyandex")) {
                            // Пользователь запустил бота по реферальной ссылке из Яндекса
                            cleanUp(userId);
                            // Удаляем флажок с указанием id канала, куда должен прилететь фидбек от пользователя
                            redisEntity.deleteElement(userId.concat("_from_chatId"));
                            if (!general.checkSubscriberIsAlreadyExist(userId)) {
                                // Сохранем ID пользователя для статистики по Яндексу
                                redisEntity.pushElement("new_users_yandex", userId);
                                // Сохранем ID пользователя для статистики по Яндексу (за текущий день)
                                redisEntity.pushElement("new_users_yandex_today", userId);
                                // Сохранем ID пользователя для общей статистики
                                redisEntity.pushElement("new_users", userId);
                            }
                            general.sendStartMessage(userId);
                        } else if (message.getText().equals("/deleteInactive") && isAdmin) {
                            // Команда для закрытия неактивных подписок ДО 17 июня + удаление из рассылки в Redis
                            this.general.deleteInactiveUsersByAdmin(userId);
                            return;
                        } else if (message.getText().equals("Главное меню 🎯")) {
                            cleanUp(userId);
                            this.general.sendMainMenuMessage(userId);
                            return;
                        } else if (redisEntity.getElement(userId.concat("_from_chatId")) != null
                                && !message.getText().equals("Выбрать спецтехнику 🚜")
                                && !message.getText().equals("Управление подписками 📬")
                                && !message.getText().equals("Написать нам ✏️")
                                && !message.getText().equals("Помощь ℹ️️")
                                && !message.getText().equals("Закрыть диалог ❌")
                                && !message.getText().equals("Главное меню 🎯")
                        ) {
                            admin.sendFeedbackMessageToTechChannel(message);
                            admin.sendFinalMessage(userId);
                            redisEntity.deleteElement(userId.concat("_from_chatId"));
                            return;
                        } else if (
                                ((redisEntity.getElement(userId.concat("_help")) != null && redisEntity.getElement(userId.concat("_help")).equals("true"))
                                        || redisEntity.getElement(userId.concat("_from_chatId")) != null)
                                        && message.getText().equals("Закрыть диалог ❌")) {
                            // Если пользователь закрыл диалог с поддержкой
                            this.admin.cancelingDialog(userId);
                            redisEntity.deleteElement(userId.concat("_from_chatId"));
                            redisEntity.deleteElement(userId.concat("_help"));
                            return;
                        } else if (redisEntity.getElement(userId.concat("_help")) != null
                                && redisEntity.getElement(userId.concat("_help")).equals("true")
                                && !message.getText().equals("Выбрать спецтехнику 🚜")
                                && !message.getText().equals("Управление подписками 📬")
                                && !message.getText().equals("Написать нам ✏️")
                                && !message.getText().equals("Помощь ℹ️️")
                                && !message.getText().equals("Главное меню 🎯️")
                        ) {
                            admin.sendFeedbackMessageToTechChannel(message);
                            admin.sendFinalMessage(userId);
                            return;
                        } else if (redisEntity.getElement(userId.concat("_email")) != null
                                && redisEntity.getElement(userId.concat("_email")).equals("true")) {
                            general.saveEmail(userId, message.getText());
                            // Создаем платеж после получения данных от пользователя
                            payment.createPayment(userId);
                        }
                        // Работа с фидбеком после отмены платной подписки
                        else if (redisEntity.getElement(userId.concat("_feedback")) != null
                                && redisEntity.getElement(userId.concat("_feedback")).equals("true")
                                && !message.getText().equals("Отмена ❌")) {
                            // Отправляем фидбек в канал с тех.поддержкой
                            this.admin.sendFeedbackMessageToTechChannel(message);
                            this.general.sendThanksToUserForHisFeedback(userId);
                        } else if (redisEntity.getElement(userId.concat("_feedback")) != null
                                && redisEntity.getElement(userId.concat("_feedback")).equals("true")
                                && message.getText().equals("Отмена ❌")) {
                            this.general.cancelFeedback(userId);
                        } else {
                            // Если пользователь потерялся и отправляет боту любое сообщение
                            logger.log(Level.SEVERE, "Message for lost users ...");
                            general.sendLostUserMessage(userId);
                        }
                    }
                    return;
                }

                // Ловим callback
                if (update.hasCallbackQuery()) {
                    CallbackQuery callbackQuery = update.getCallbackQuery();
                    Message callbackMessage = callbackQuery.getMessage();
                    logger.log(Level.INFO, "callbackQuery - {0}", new Gson().toJson(callbackQuery));
                    logger.log(Level.SEVERE, "callbackQuery data - {0}", callbackQuery.getData());

                    String callBackData = callbackQuery.getData();
                    String callBackQueryId = callbackQuery.getId();
                    String userId = String.valueOf(callbackMessage.getChat().getId());
                    Integer messageId = callbackMessage.getMessageId();
                    String text = callbackMessage.getText();
                    Matcher matcher = PATTERN.matcher(callBackData);

                    //  Работа с главным меню
                    if (callBackData.equals("choose_machine")) {
                        cleanUp(userId);
                        general.getChoiceHeavyMachineryTypes(userId);
                        this.sendAnswerCallbackQuery(callBackQueryId);
                        return;
                    } else if (callBackData.equals("sub_management")) {
                        cleanUp(userId);
                        general.getAllActiveAndTrialSubscriptionsForUser(userId);
                    } else if (callBackData.equals("support")) {
                        cleanUp(userId);
                        // Удаляем флажок с указанием id канала, куда должен прилететь фидбек от пользователя
                        redisEntity.deleteElement(userId.concat("_from_chatId"));
                        general.requestProblem(userId);
                        this.sendAnswerCallbackQuery(callBackQueryId);
                        return;
                    } else if (callBackData.equals("help")) {
                        cleanUp(userId);
                        general.getHelpInfo(userId);
                        this.sendAnswerCallbackQuery(callBackQueryId);
                        return;
                    } else if (callBackData.equals("statistic")) {
                        boolean isAdmin = redisEntity.getElement(userId.concat("_isAdmin")) != null
                                && !redisEntity.getElement(userId.concat("_isAdmin")).isEmpty()
                                && redisEntity.getElement(userId.concat("_isAdmin")).equals("true");
                        if (!isAdmin) {
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }
                        cleanUp(userId);
                        statistic.sendStatisticMessage(userId);
                        this.sendAnswerCallbackQuery(callBackQueryId);
                        return;
                    }
                    // Просмотр номера телефона
                    // Проверяем, есть ли сейчас автоплатежи по подписке
                    // Показывает номер телефона, если есть автоплатежи в течение 6 часов после оплаты
                    // Если прошло, то выводи что заказ не актуален
                    else if (callBackData.equals("show_phone")) {
                        if (text == null || text.isEmpty()) {
                            logger.log(Level.SEVERE, "Text for messageId - {0} is null! Don't show the phone...", messageId);
                            return;
                        }
                        
                        LocalDateTime expirationTime = orderCreatedTime.plusHours(EXPIRATION_HOURS); // Добавляем EXPIRATION_HOURS часов к моменту создания заказа
                        LocalDateTime currentTime = LocalDateTime.now(); // Получаем текущее время
                    
                        if (currentTime.isAfter(expirationTime)) {
                            // Время заказа истекло, номер телефона больше не может быть показан
                            logger.log(Level.INFO, "Заказ уже не актуален. Номер телефона не может быть показан.");
                            return;
                        }
                    
                        general.showPhoneNumber(userId, messageId, text);
                    }

                    // Просмотр стоимости подписок
                    else if (callBackData.equals("price")) {
                        this.general.getPriceInfo(userId);
                    }
                    // Проверяем callBackData по списку типов спецтехники в Redis
                    else if (redisEntity.getElements("heavyMachineryIds") != null
                            && redisEntity.getElements("heavyMachineryIds").contains(callBackData)) {

                        // Проверяем, есть ли сейчас автоплатежи по подписке
                        if (general.checkAutoPaySubscription(userId, callBackData)) {
                            general.sendHaveAutoPayment(userId, callBackData);
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }

                        // Проверяем наличие данной подписки в БД
                        if (general.checkSubscriptionForUserId(userId, callBackData)) {
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }
                        // Сохраняем в Redis id пользователя для дальнейшей рассылки по выбранной спецтехнике
                        redisEntity.pushElement(callBackData, userId);
                        // Сохраняем в БД инфу по подписчику: userId, тип подписки + кол-во бесплатных коннектов для текущего пользователя и выбранного типа спецтехники
                        general.addSubscriberInfo(userId, callBackData);
                        // Оповещаем пользователя
                        general.sendMessageAboutChoice(userId, callBackData);
                    } else if (matcher.matches()) {
                        // Пользователь выбрал тариф для оформления подписки (неделя/месяц/год)
                        // Получаем тип спецтехники и длительность выбранной подписки
                        String equipmentType = callBackData.substring(callBackData.indexOf('_') + 1);
                        String subscriptionType = callBackData.substring(0, callBackData.indexOf('_'));

                        // Проверяем, есть ли сейчас автоплатежи по подписке
                        if (general.checkAutoPaySubscription(userId, equipmentType)) {
                            general.sendHaveAutoPayment(userId, equipmentType);
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }

                        // Проверяем, есть ли активная подписка на данную спецтехнику
                        if (general.checkActiveSubscription(userId, equipmentType)) {
                            general.sendAlreadyHaveSubscription(userId, equipmentType);
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }

                        // Дополнительно проверяем, есть ли триальная подписка с 0 коннектов на данный тип спецтехники.
                        // Если нет, то не разрешаем оформлять платную подписку
                        if (!general.checkTrialSubscription(userId, equipmentType)) {
                            general.sendDontHaveTrialSubscription(userId, equipmentType);
                            this.sendAnswerCallbackQuery(callBackQueryId);
                            return;
                        }

                        // Сохраняем тип подписки и тип спецтехники
                        redisEntity.hashSet(userId, "equipmentType", equipmentType);
                        redisEntity.hashSet(userId, "subscriptionType", subscriptionType);
                        // Удаляем флажок "_from_chatId" с указанием id канала, куда должен прилететь фидбек от пользователя,
                        // чтобы введенный email не улетел в поддержку
                        // + Дополнительно удаляем флажки, касающиеся поддержки
                        redisEntity.deleteElement(userId.concat("_from_chatId"));
                        redisEntity.deleteElement(userId.concat("_help"));
                        redisEntity.deleteElement(userId.concat("_feedback"));
                        // Запрашиваем email, чтобы можно было отправить чек
                        redisEntity.setElement(userId.concat("_email"), "true");
                        general.sendRequestEmail(userId);
                    } else if (callBackData.contains("_unsubscribe")) {
                        String equipmentType = callBackData.substring(0, callBackData.indexOf('_'));
                        general.unsubscribe(userId, equipmentType, messageId);
                    } else if (callBackData.equals("main_menu")) {
                        cleanUp(userId);
                        general.sendMainMenuMessage(userId);
                    }
                    // Статистика
                    else if (callBackData.equals("count_of_trial_subscr")) {
                        statistic.getCountOfSubscriptionsForEachUser(userId, "Trial", messageId);
                    } else if (callBackData.equals("count_of_paid_subscr")) {
                        statistic.getCountOfSubscriptionsForEachUser(userId, "Active", messageId);
                    } else if (callBackData.equals("count_of_trial_subscr_by_equipment")) {
                        statistic.getCountOfSubscriptionsForEachEquipment(userId, "Trial", messageId);
                    } else if (callBackData.equals("count_of_paid_subscr_by_equipment")) {
                        statistic.getCountOfSubscriptionsForEachEquipment(userId, "Active", messageId);
                    } else if (callBackData.equals("count_and_duration_of_paid_subscr_by_equipment")) {
                        statistic.getCountAndDurationOfPaidSubscriptionsForEachEquipment(userId, messageId);
                    } else if (callBackData.equals("count_of_new_user_by_day")) {
                        statistic.getCountOfNewBotUsers(userId, messageId);
                    } else if (callBackData.equals("count_of_user")) {
                        statistic.getCountOfBotUsers(userId, messageId);
                    } else if (callBackData.equals("back_to_main_menu")) {
                        statistic.sendStatisticMessage(userId, messageId);
                    } else if (callBackData.equals("count_of_ya_user")) {
                        statistic.getCountOfYaUsers(userId, messageId);
                    }
                    // Фидбек при отмене подписки
                    else if (callBackData.equals("write_feedback")) {
                        this.cleanUp(userId);
                        this.general.feedbackRequest(userId);
                    }
                    this.sendAnswerCallbackQuery(callBackQueryId);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private void cleanUp(String chatId) {
        redisEntity.deleteElement(chatId.concat("_subscription"));
        redisEntity.deleteElement(chatId.concat("_email"));
        redisEntity.deleteElement(chatId.concat("_help"));
        redisEntity.deleteElement(chatId.concat("_feedback"));
    }

    private void sendAnswerCallbackQuery(String callBackQueryId) {
        // Через answerCallbackQuery убираем значок загрузки на кнопке
        try {
            execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callBackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
