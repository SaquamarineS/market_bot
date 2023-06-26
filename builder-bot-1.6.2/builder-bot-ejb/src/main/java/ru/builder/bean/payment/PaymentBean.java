package ru.builder.bean.payment;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import okhttp3.*;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.builder.bean.admin.Admin;
import ru.builder.bean.firebase.Firebase;
import ru.builder.bean.general.General;
import ru.builder.bot.BuilderBot;
import ru.builder.db.payment.PaymentOperations;
import ru.builder.db.subscriber.SubscriberOperations;
import ru.builder.db.subscription.SubscriptionOperations;
import ru.builder.model.payment.*;
import ru.builder.model.payment.receipt.Customer;
import ru.builder.model.payment.receipt.Item;
import ru.builder.model.payment.receipt.Receipt;
import ru.builder.model.subscr.Subscriber;
import ru.builder.model.subscr.Subscription;
import ru.builder.model.subscr.SubscriptionType;
import ru.builder.redis.RedisEntity;

import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class PaymentBean implements Payment {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    private final OkHttpClient client = new OkHttpClient();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Inject
    private SubscriptionOperations subscriptionOperations;
    @Inject
    private SubscriberOperations subscriberOperations;
    @Inject
    private PaymentOperations paymentOperations;
    @Inject
    private General general;
    @Inject
    private Admin admin;
    @Inject
    private Firebase firebase;
    @Inject
    private RedisEntity redisEntity;

    @Override
    public void createPayment(String userId) {
        String email = redisEntity.hashGet(userId, "email");
        String equipmentType = redisEntity.hashGet(userId, "equipmentType");
        String subscriptionType = redisEntity.hashGet(userId, "subscriptionType");

        // Получаем информацию о подписке
        Subscription subscription = subscriptionOperations.getSubscriptionInfo(subscriptionType, equipmentType);

        // Подготавливаем данные для создания платежа в ЮKassa
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        Gson gson = gsonBuilder.create();

        // Если описание превышает 128 символов, то обрезаем до 128 символов принудительно
        String description = subscription.getDescription().concat("/").concat(firebase.getHeavyMachineryNameById(equipmentType)).concat("/").concat(email);

        if (description.length() > 128) {
            description = description.substring(0, 128);
        }

        String receiptDescription = "Спецтехника ".concat(firebase.getHeavyMachineryNameById(equipmentType).toUpperCase()).concat(". ").concat(subscription.getDescription());

        // Receipt: Формирование чека
        Receipt receipt = new Receipt(
                new Customer(email),
                Collections.singletonList(new Item(receiptDescription,
                        "1",
                        new Amount(subscription.getAmount(), "RUB"),
                        1)));

        CreatePayment createPayment = new CreatePayment(
                new PaymentMethodData("bank_card"),
                "true",
                "true",
                receipt);

        createPayment.setDescription(description);
        createPayment.setConfirmation(new Confirmation("redirect", redisEntity.getElement("returnUrl")));
        createPayment.setAmount(new Amount(subscription.getAmount(), "RUB"));

        logger.log(Level.SEVERE, "Create payment - {0} for userId - {1}", new Object[]{gson.toJson(createPayment), userId});

        // Отправляем данные на создание платежа в ЮKassa
        String authorization = Credentials.basic(System.getenv("shopId"), System.getenv("secret_key"));
        RequestBody body = RequestBody.create(gson.toJson(createPayment), MediaType.parse("application/json"));
        String createPaymentResponse = null;
        Request request = new Request.Builder()
                .url("https://api.yookassa.ru/v3/payments")
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .header("Idempotence-Key", UUID.randomUUID().toString())
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            logger.log(Level.SEVERE, "POST response status is - {0}", response.code());

            if (response.body() != null) {
                createPaymentResponse = response.body().string();
                logger.log(Level.SEVERE, "createPaymentResponse - {0}", createPaymentResponse);
            }

            // Если произошла непредвиденная ошибка при создании платежа, то просим пользователя обратиться к администратору
            if (response.code() != 200) {
                logger.log(Level.SEVERE, "Unexpected error for creating payment! - {0}", createPaymentResponse);
                general.sayUnexpectedError(userId, "❗️При создании платежа произошла ошибка😕\n" +
                        "ℹ️Возможная причина ошибки: Некорректно введен email адрес " + email + "\n\n" +
                        "Если ошибка повторится -");
                admin.sendErrorMessagesToTechChannel("❗️При создании платежа произошла непредвиденная ошибка! Что-то пошло не так!\n".concat("Json response - " + createPaymentResponse));
                return;
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }


        ru.builder.model.payment.Payment payment = null;

        try {
            payment = gson.fromJson(createPaymentResponse, ru.builder.model.payment.Payment.class);
        } catch (JsonSyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        if (payment == null) {
            logger.log(Level.SEVERE, "Payment object is null, return ...");
            return;
        }

        String message = "*Счет на оплату сформирован:*\n"
                .concat("*ID платежа:* `").concat(payment.getId()).concat("`\n")
                .concat("*Платежная система:* ЮKassa\n")
                .concat("*Длительность подписки:* " + subscription.getDescription() + "\n")
                .concat("*Тип спецтехники:* " + firebase.getHeavyMachineryNameById(equipmentType) + "\n")
                .concat("*Сумма:* " + subscription.getAmount() + " руб. \n")
                .concat("Нажмите на кнопку 💰Оплатить, чтобы завершить процесс оплаты\n\n")
                .concat("*После оплаты в течение минуты я оповещу Вас о статусе платежа.\n\nЕсли Вы не получили сообщение в течение минуты - сообщите об этом в поддержку*")
                .concat("\n\nВы можете отписаться от платной подписки в любой момент, нажав на кнопку ГЛАВНОЕ МЕНЮ, затем УПРАВЛЕНИЕ ПОДПИСКАМИ.");

        // Отправляем пользователю ссылку на совершение оплаты
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(message
                        .replaceAll("\\.", "\\\\.")
                        .replaceAll("\\,", "\\\\,")
                        .replaceAll("\\:", "\\\\:")
                        .replaceAll("\\!", "\\\\!")
                        .replaceAll("\\-", "\\\\-")
                        .replaceAll("\\)", "\\\\)")
                        .replaceAll("\\(", "\\\\("))
                .replyMarkup(getPaymentButton(payment.getConfirmation().getConfirmationUrl()))
                .parseMode(ParseMode.MARKDOWNV2)
                .build();
        try {
            // Отправляем сообщение с выставленным счетом
            Message paymentMessage = new BuilderBot().execute(sendMessage);

            // Получаем текущий статус подписки по данной спецтехнике
            Subscriber subscriber = subscriberOperations.getActiveOrTrialSubscriberByIdAndEquipmentType(userId, equipmentType);

            // Если есть данные по подписчику, статус подписки "Trial" и есть PaymentId
            if (subscriber != null
                    && subscriber.getStatus().equals("Trial")
                    && subscriber.getPaymentId() != null
                    && !subscriber.getPaymentId().isEmpty()) {
                // Получаем информацию о платеже по PaymentId
                ru.builder.model.payment.Payment oldPayment = paymentOperations.getPaymentByPaymentId(subscriber.getPaymentId());
                // Вытаскиваем messageId для удаления сообщения с выставленным счетом
                String messageId = oldPayment.getMessageId();

                if (messageId != null) {
                    try {
                        // Удаляем сообщение
                        DeleteMessage deleteMessage = DeleteMessage.builder()
                                .chatId(userId)
                                .messageId(Integer.parseInt(messageId))
                                .build();
                        new BuilderBot().execute(deleteMessage);
                    } catch (TelegramApiException ex) {
                        logger.log(Level.SEVERE, "Can't delete the message, because - {0}", ex.getMessage());
                    }

                    // Даже если не удалось удалить сообщение со старым платежом - все равно закрываем в БД старый платеж!
                    // Проставляем платежу статус "canceled" и причину, что был создан новый платеж
                    // Таким образом при проверке статуса старый платеж не будет учтен
                    // Также пользователь не сможет оплатить старый платеж, который не будет учтен
                    oldPayment.setStatus("canceled");
                    oldPayment.setCancellationDetails(new CancellationDetails("merchant", "Был создан новый платеж"));
                    paymentOperations.setCanceledPaymentInfo(oldPayment);
                }
            }

            // Фиксируем в БД информацию по платежу
            paymentOperations.addPayment(payment, userId, String.valueOf(paymentMessage.getMessageId()));

            // Обновляем в БД информацию о подписчике
            subscriberOperations.updateSubscriber(new Subscriber(
                    userId,
                    payment.getId(),
                    email,
                    subscriptionType,
                    equipmentType));

        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void checkPaymentsStatus() {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        Gson gson = gsonBuilder.create();

        List<String> ids = paymentOperations.getPaymentIdsWithPendingStatus();
        String authorization = Credentials.basic(System.getenv("shopId"), System.getenv("secret_key"));

        ids.forEach(id -> {

            Subscriber subscriber = subscriberOperations.getSubscriberByPaymentId(id);

            if (subscriber == null) {
                logger.log(Level.SEVERE, "Subscriber with paymentId - {0} is null, return...", id);
                return;
            }

            // Отправляем данные для проверки статуса платежа в ЮKassa
            Request request = new Request.Builder()
                    .url("https://api.yookassa.ru/v3/payments/".concat(id))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("Idempotence-Key", UUID.randomUUID().toString())
                    .get()
                    .build();

            String checkPaymentResponse = null;

            try (Response response = client.newCall(request).execute()) {

                logger.log(Level.SEVERE, "GET response status is - {0}", response.code());

                if (response.body() != null) {
                    checkPaymentResponse = response.body().string();
                    logger.log(Level.SEVERE, "checkPaymentResponse - {0}", checkPaymentResponse);
                }

                // Если при проверке статуса произошла непредвиденная ошибка, то отправляем в Тех. канал информацию об ошибке.
                if (response.code() != 200) {
                    logger.log(Level.SEVERE, "Unexpected error when checking payment status with payment id - {0}, Error - {1}.", new Object[]{id, checkPaymentResponse});
                    admin.sendErrorMessagesToTechChannel("❗️При получении статуса платежа произошла непредвиденная ошибка!\n".concat("Json response - " + checkPaymentResponse));
                    return;
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, null, e);
            }

            ru.builder.model.payment.Payment payment = null;

            try {
                payment = gson.fromJson(checkPaymentResponse, ru.builder.model.payment.Payment.class);
            } catch (JsonSyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

            if (payment == null) {
                logger.log(Level.SEVERE, "Payment object is null, return ...");
                return;
            }

            // Если статус платежа в системе ЮKassa 'pending', то берем следующий платеж
            if (payment.getStatus().equals("pending"))
                return;

            // Если статус canceled - фиксируем в БД информацию о причине и оповещаем пользователя
            if (payment.getStatus().equals("canceled")) {
                logger.log(Level.SEVERE, "Payment status is canceled - {0}", gson.toJson(payment));
                paymentOperations.setCanceledPaymentInfo(payment);
                general.sendMessageAboutCanceledPayment(subscriber.getUserId(), payment.getCancellationDetails().getReason());
                return;
            }

            payment.setUserId(subscriber.getUserId());
            // Если статус succeeded - обновляем в БД информацию по платежу и выдаем пользователю доступ к боту

            // Определяем дату следующего платежа
            Calendar nextBillCalendar = null;

            if (payment.getCapturedAt() != null && convertDate(payment.getCapturedAt()) != null) {
                nextBillCalendar = Calendar.getInstance();
                nextBillCalendar.setTime(convertDate(payment.getCapturedAt()));

                Subscription subscription = subscriptionOperations.getSubscriptionInfo(subscriber.getSubscriptionType(), subscriber.getEquipmentType());

                if (subscriber.getSubscriptionType().equals(SubscriptionType.week.value())) {
                    nextBillCalendar.add(Calendar.WEEK_OF_MONTH, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.month.value())) {
                    nextBillCalendar.add(Calendar.MONTH, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.year.value())) {
                    nextBillCalendar.add(Calendar.YEAR, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.test.value())) {
                    nextBillCalendar.add(Calendar.MINUTE, subscription.getDuration());
                }

                String nextBillDate = dateFormat.format(nextBillCalendar.getTime());
                logger.log(Level.INFO, "NextBillDate for payment - {0} is - {1}", new Object[]{payment.getId(), nextBillDate});
                payment.setNextBillDate(nextBillDate);
            } else {
                logger.log(Level.SEVERE, "Calendar is null for CapturedAt - {0}. Don't add info about next payment for paymentId - {1}", new Object[]{payment.getCapturedAt(), payment.getId()});
            }

            paymentOperations.setSucceededPaymentInfo(payment);
            // Также обновляем статус подписки в таблице Subscribers
            subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), payment.getId(), payment.getCapturedAt(), false);

            // Оповещаем пользователя об успешной оплате
            general.sendMessageAboutSuccessfulPayment(subscriber.getUserId(), subscriber.getEquipmentType());

            // Если удалось установить дату следующего платежа, то создаем таску на автоплатеж
            if (nextBillCalendar != null
                    && payment.getNextBillDate() != null
                    && !payment.getNextBillDate().isEmpty()) {
                this.createAutoPayment(payment, nextBillCalendar);
            }
        });

    }

    /**
     * Создает автоматический платеж на основе переданного объекта payment и даты следующего счета nextBillDate.
     * Задержка выполнения платежа определяется разницей между nextBillDate и текущим временем.
     * Если задержка отрицательная, то платеж будет выполнен немедленно.
     * Созданный автоматический платеж добавляется в планировщик задач для выполнения в указанное время.
     * Пользователь оповещается о предстоящем окончании подписки, если она будет завершена через 24 часа или менее.
     *
     * @param payment       Объект платежа, на основе которого будет создан автоматический платеж.
     * @param nextBillDate  Дата следующего счета, когда должен быть выполнен автоматический платеж.
     */
    public void createAutoPayment(Payment payment, Calendar nextBillDate) {
        long delay = nextBillDate.getTime().getTime() - Calendar.getInstance().getTimeInMillis();
        logger.log(Level.SEVERE, "Create autoPayment task for paymentId - {0} with delay - {1}", new Object[]{payment.getId(), delay});

        // Получение информации о подписчике и подписке
        Subscriber subscriber = subscriberOperations.getSubscriberByPaymentId(payment.getId());
        Subscription subscription = subscriptionOperations.getSubscriptionInfo(subscriber.getSubscriptionType(), subscriber.getEquipmentType());

        // Формирование описания чека
        String receiptDescription = "Спецтехника ".concat(firebase.getHeavyMachineryNameById(subscriber.getEquipmentType()).toUpperCase()).concat(". ").concat(subscription.getDescription());

        // Создание объекта чека
        Receipt receipt = new Receipt(
                new Customer(subscriber.getEmail()),
                Collections.singletonList(new Item(receiptDescription,
                        "1",
                        new Amount(payment.getAmount().getValue(), payment.getAmount().getCurrency()),
                        1)));

        // Создание объекта CreatePayment для автоматического платежа
        CreatePayment createPayment = new CreatePayment(payment.getPaymentMethod().getId(), "true", receipt);
        createPayment.setAmount(payment.getAmount());

        // Установка описания платежа с префиксом "Автоплатеж:", если оно еще не содержит этого префикса
        if (!payment.getDescription().contains("Автоплатеж:")) {
            createPayment.setDescription("Автоплатеж: " + payment.getDescription());
        } else {
            createPayment.setDescription(payment.getDescription());
        }

        // Планирование задачи для выполнения автоматического платежа с указанной задержкой
        executorService.schedule(() -> this.createScheduleTaskForAutoPayment(createPayment, payment.getId(), nextBillDate, false), delay, TimeUnit.MILLISECONDS);

        // Если задержка отрицательная, оповещение пользователя о завершении подписки не требуется
        if (delay < 0) {
            logger.log(Level.SEVERE, "delay - {0} is negative, don't broadcasting user about expired subscription ...", delay);
            return;
        }

        // Оповещение пользователя о предстоящем окончании подписки
        // Если подписка тестовая, оповещение отправляется за 2 минуты до окончания
        // В противном случае, оповещение отправляется за 24 часа до окончания
        if (subscription.getSubscriptionType() == SubscriptionType.test) {
            nextBillDate.add(Calendar.MINUTE, -2);
        } else {
            nextBillDate.add(Calendar.HOUR, -24);
        }
        general.sendWarningMessageAboutSubscriptionWillBeExpired(payment.getUserId(), payment.getId(), nextBillDate);
    }


    @Override
    public void retryErrorPayments() {
        logger.log(Level.SEVERE, "Getting retry error payments ...");
        //Получаем из БД все платежи в статусе "error" и выполняем повторный автоплатеж
        List<CreatePayment> errorPayments = paymentOperations.getErrorPaymentsByStatus("error");

        if (errorPayments == null) {
            logger.log(Level.SEVERE, "Error payments wasn't found...");
            return;
        }

        errorPayments.forEach(autoPayment -> {

            Subscriber subscriber = subscriberOperations.getSubscriberByPaymentId(autoPayment.getPaymentMethodId());

            if (autoPayment.getTryCount() == 3) {
                logger.log(Level.SEVERE, "Auto payment was send 3 times, return user warning message");
                // Оповещаем пользователя, что после 3 попыток не удалось выполнить автоплатеж
                general.sayUnexpectedError(subscriber.getUserId(), "❗Что-то пошло не так! Мне не удалось провести автоплатеж с 3 попыток.\nПросьба не оформлять новую подписку!");
                // Для ошибочного платежа в БД фиксируем финальный статус failed, чтобы не брать в дальнейшем данный платеж в обработку
                paymentOperations.setNewStatusForErrorAutoPayment(autoPayment.getPaymentMethodId(), "failed", "error");
                admin.sendErrorMessagesToTechChannel("❗Падают автоплатежи! С 3 попыток не прошли! Что-то пошло не так!");
                return;
            }

            logger.log(Level.SEVERE, "Send RETRY create autoPayment ...");

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            Gson gson = gsonBuilder.create();

            logger.log(Level.SEVERE, "Create RETRY autoPayment - {0}", gson.toJson(autoPayment));

            // Отправляем данные на создание платежа в ЮKassa
            String authorization = Credentials.basic(System.getenv("shopId"), System.getenv("secret_key"));
            RequestBody body = RequestBody.create(gson.toJson(autoPayment), MediaType.parse("application/json"));
            String createPaymentResponse = null;
            Request request = new Request.Builder()
                    .url("https://api.yookassa.ru/v3/payments")
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .header("Idempotence-Key", UUID.randomUUID().toString())
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {

                logger.log(Level.SEVERE, "POST response status is - {0}", response.code());

                if (response.body() != null) {
                    createPaymentResponse = response.body().string();
                    logger.log(Level.SEVERE, "createPaymentResponse - {0}", createPaymentResponse);
                }

                // Если платеж ОПЯТЬ ошибочный - фиксируем + 1 повтор и ждем следующего переповтора
                if (response.code() != 200) {
                    logger.log(Level.SEVERE, "Unexpected error for auto payment with payment method id - {0}, Error - {1}\nAdd retry count value +1 ", new Object[]{autoPayment.getPaymentMethodId(), createPaymentResponse});
                    paymentOperations.setRetryCountForErrorAutoPayment(autoPayment.getPaymentMethodId(), autoPayment.getTryCount());
                    return;
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, null, e);
            }

            // Обрабатываем ответ
            ru.builder.model.payment.Payment payment = null;

            try {
                payment = gson.fromJson(createPaymentResponse, ru.builder.model.payment.Payment.class);
            } catch (JsonSyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

            if (payment == null) {
                logger.log(Level.SEVERE, "Payment object is null, return ...");
                return;
            }

            // Если статус canceled - фиксируем в БД информацию о причине и оповещаем пользователя
            // Также выполняем повтор автоплатежа: через 2 дня и через 5 дней
            if (payment.getStatus().equals("canceled")) {
                logger.log(Level.SEVERE, "Payment status is canceled - {0}", gson.toJson(payment));
                this.paymentOperations.setNewStatusForErrorAutoPayment(autoPayment.getPaymentMethodId(), "canceled", "error");
                // Оповещаем пользователя, что автоплатеж не прошел и будет выполнено еще две попытки: через 2 дня и через 5 дней
                general.sendMessageAboutCanceledAutoPayment(subscriber.getUserId(), payment.getCancellationDetails().getReason());
                return;
            }

            Calendar nextBillCalendar = null;
            if (payment.getCapturedAt() != null && convertDate(payment.getCapturedAt()) != null) {
                nextBillCalendar = Calendar.getInstance();
                nextBillCalendar.setTime(convertDate(payment.getCapturedAt()));

                Subscription subscription = subscriptionOperations.getSubscriptionInfo(subscriber.getSubscriptionType(), subscriber.getEquipmentType());

                if (subscriber.getSubscriptionType().equals(SubscriptionType.week.value())) {
                    nextBillCalendar.add(Calendar.WEEK_OF_MONTH, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.month.value())) {
                    nextBillCalendar.add(Calendar.MONTH, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.year.value())) {
                    nextBillCalendar.add(Calendar.YEAR, subscription.getDuration());
                } else if (subscriber.getSubscriptionType().equals(SubscriptionType.test.value())) {
                    nextBillCalendar.add(Calendar.MINUTE, subscription.getDuration());
                }

                String nextBillDate = dateFormat.format(nextBillCalendar.getTime());
                logger.log(Level.INFO, "NextBillDate for auto payment - {0} is - {1}", new Object[]{payment.getId(), nextBillDate});
                payment.setNextBillDate(nextBillDate);
            } else {
                logger.log(Level.SEVERE, "Calendar is null for CapturedAt - {0}. Don't add info about next payment for auto payment with paymentId - {1}", new Object[]{payment.getCapturedAt(), payment.getId()});
            }

            // Если платеж прошел успешно, то проставляем в ErrorPayments для данного paymentMethodId статус success, чтобы его не брать повторно в обработку
            paymentOperations.setNewStatusForErrorAutoPayment(autoPayment.getPaymentMethodId(), "success", "error");
            // Добавляем в таблицу Payments новый платеж
            paymentOperations.addAutoPayment(payment, autoPayment.getUserId());
            // Добавляем в таблицу Subscribers новую строчку с подпиской в статусе 'Active'
            Subscriber subscriberAfterAutoPayment = subscriberOperations.getSubscriberByPaymentId(autoPayment.getPaymentMethodId());
            subscriberAfterAutoPayment.setPaymentId(payment.getId());
            subscriberOperations.addSubscriberAfterAutoPayment(subscriberAfterAutoPayment, payment.getCapturedAt());

            // Сообщаем пользователю, что автоплатеж прошел успешно
            general.sendMessageAboutSuccessfullyAutoPayment(subscriberAfterAutoPayment.getUserId(), subscriber.getEquipmentType());

            // Если удалось установить дату следующего платежа, то создаем таску на следующий автоплатеж
            if (nextBillCalendar != null
                    && payment.getNextBillDate() != null
                    && !payment.getNextBillDate().isEmpty()) {
                this.createAutoPayment(payment, nextBillCalendar);
            }
        });
    }

    @Override
    public void retryCanceledPayments() {
        logger.log(Level.SEVERE, "Getting retry canceled payments ...");
        //Получаем из БД все платежи в статусе "canceled" и выполняем повторный автоплатеж
        List<CreatePayment> canceledPayments = paymentOperations.getErrorPaymentsByStatus("canceled");

        if (canceledPayments == null
                || canceledPayments.isEmpty()) {
            logger.log(Level.SEVERE, "Canceled payments wasn't found...");
            return;
        }

        canceledPayments.forEach(payment -> {
            // Получаем дату ошибки по paymentMethodId платежа из БД
            String errorDate = this.paymentOperations.getErrorDateByPaymentMethodId(payment.getPaymentMethodId());
            if (errorDate == null) {
                logger.log(Level.SEVERE, "PaymentMethodId - {0}: errorDate is null, return ...", payment.getPaymentMethodId());
                return;
            }

            logger.log(Level.SEVERE, "PaymentMethodId - {0}: errorDate - {1}", new Object[]{payment.getPaymentMethodId(), errorDate});
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // Конвертируем дату ошибки в LocalDateTime
            LocalDateTime errorDateTime = LocalDateTime.parse(errorDate, formatter);
            logger.log(Level.SEVERE, "PaymentMethodId - {0}: errorDateTime - {1}", new Object[]{payment.getPaymentMethodId(), errorDateTime});

            // Если tryCount = 0, значит запускаем повтор автоплатежа через 2 дня
            // В billDate передаем текущую дату, так как если платеж будет завершен, то текущей датой
            if (payment.getTryCount() == 0) {
                // Прибавляем к дате ошибки 2 дня
                LocalDateTime plusTwoDaysDateTime = errorDateTime.plusDays(2);
                logger.log(Level.SEVERE, "PaymentMethodId - {0}: plusTwoDaysDateTime - {1}", new Object[]{payment.getPaymentMethodId(), plusTwoDaysDateTime});

                // Получаем разницу между текущей датой и датой выполнения повтора автоплатежа в миллисекундах
                // Эту разницу подставляем в executorService для запуска отложенной задачи
                long retryMilliSec = ChronoUnit.MILLIS.between(LocalDateTime.now(), plusTwoDaysDateTime);
                logger.log(Level.SEVERE, "PaymentMethodId - {0}: retryMilliSec - {1}", new Object[]{payment.getPaymentMethodId(), retryMilliSec});

                logger.log(Level.SEVERE, "PaymentMethodId - {0}: Try count value = 0, start task after 2 days ...", payment.getPaymentMethodId());
                this.executorService.schedule(() ->
                                this.createScheduleTaskForAutoPayment(
                                        payment,
                                        payment.getPaymentMethodId(),
                                        Calendar.getInstance(), true),
                        retryMilliSec,
                        TimeUnit.MILLISECONDS);

                // Увеличиваем tryCount
                this.paymentOperations.setRetryCountForErrorAutoPayment(payment.getPaymentMethodId(), 0);
                // Меняем статус платежа
                this.paymentOperations.setNewStatusForErrorAutoPayment(payment.getPaymentMethodId(), "wait", "canceled");
            }
            // Если tryCount = 2, значит запускаем повтор автоплатежа через 3 дня
            // В billDate передаем текущую дату, так как если платеж будет завершен, то текущей датой
            else if (payment.getTryCount() == 2) {
                // Прибавляем к дате ошибки 5 дней (то есть 2 + 3)
                LocalDateTime plusThreeDaysDateTime = errorDateTime.plusDays(5);
                logger.log(Level.SEVERE, "PaymentMethodId - {0}: plusThreeDaysDateTime - {1}", new Object[]{payment.getPaymentMethodId(), plusThreeDaysDateTime});

                // Получаем разницу между текущей датой и датой выполнения повтора автоплатежа в миллисекундах
                // Эту разницу подставляем в executorService для запуска отложенной задачи
                long retryMilliSec = ChronoUnit.MILLIS.between(LocalDateTime.now(), plusThreeDaysDateTime);
                logger.log(Level.SEVERE, "PaymentMethodId - {0}: retryMilliSec - {1}", new Object[]{payment.getPaymentMethodId(), retryMilliSec});

                logger.log(Level.SEVERE, "PaymentMethodId - {0}: Try count value = 1, start task after 3 days ...", payment.getPaymentMethodId());
                this.executorService.schedule(() ->
                                this.createScheduleTaskForAutoPayment(
                                        payment,
                                        payment.getPaymentMethodId(),
                                        Calendar.getInstance(), true),
                        retryMilliSec,
                        TimeUnit.MILLISECONDS);

                // Меняем статус платежа
                this.paymentOperations.setNewStatusForErrorAutoPayment(payment.getPaymentMethodId(), "wait", "canceled");
            }
        });
    }

    private void createScheduleTaskForAutoPayment(CreatePayment createPayment, String paymentId, Calendar billDate, boolean isRetry) {
        logger.log(Level.INFO, "Start createScheduleTaskForAutoPayment - {0}", new Gson().toJson(createPayment));

        // По ID платежа получаем информацию о подписке
        Subscriber subscriber = subscriberOperations.getSubscriberByPaymentId(paymentId);
        logger.log(Level.INFO, "Subscriber info for paymentId - {0}: - {1}", new Object[]{paymentId, new Gson().toJson(subscriber)});

        if (subscriber == null) {
            logger.log(Level.SEVERE, "Method: createScheduleTaskForAutoPayment >> Subscriber with paymentId - {0} is null, return...", paymentId);
            return;
        }

        // Если статус подписки 'Expired', значит ЛИБО выполняется уже НЕ первый автоплатеж, ЛИБО подписка была отменена ДО самого первого автоплатежа
        // В этом случае получаем подписку по ID пользователя, ID спецтехники и в статусе 'Active' или 'AutoPay'
        if (subscriber.getStatus().equals("Expired")) {
            String userId = subscriber.getUserId();
            String equipmentType = subscriber.getEquipmentType();
            logger.log(Level.SEVERE, "Status for paymentId - {0} is Expired. Get Subscriber info by userId - {1}, equipmentType - {2} and subscription status is 'Active' or 'AutoPay'...", new Object[]{paymentId, userId, equipmentType});
            subscriber = subscriberOperations.getSubscriberByIdAndEquipmentTypeAndActiveOrAutoPayStatus(userId, equipmentType);

            // Если такой подписки не найдено, значит просто завершаем работу метода
            if (subscriber == null) {
                logger.log(Level.SEVERE, "Method: createScheduleTaskForAutoPayment >> Subscriber by userId - {0}, equipmentType - {1} and subscription status is 'Active' or 'AutoPay' is null, return...", new Object[]{userId, equipmentType});
                // Также в таблице ErrorPayments закрываем запись, проставляя статус 'stop', если пришел флажок isRetry = true (То есть вызов метода был из retryCanceledPayments())
                if (isRetry)
                    this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "stop", "wait");
                return;
            }
            logger.log(Level.SEVERE, "Subscriber info by userId - {0}, equipmentType - {1} and subscription status is 'Active' or 'AutoPay': {2}", new Object[]{subscriber.getUserId(), subscriber.getEquipmentType(), new Gson().toJson(subscriber)});

            // Если пришла задача на выполнение автоплатежа, при этом выполняется ПОВТОРНЫЙ автоплатеж (isRetry = true)
            // и у пользователя на данный момент уже есть Активная подписка на эту спецтехнику -
            // значит автоплатеж НЕ ВЫПОЛНЯЕМ, так как подписчик отменил прошлую подписку и оформил новую
            if (isRetry && subscriber.getStatus().equals("Active")) {
                logger.log(Level.SEVERE, "Method: createScheduleTaskForAutoPayment >> Subscriber: paymentId - {0}, userId - {1}, equipmentType - {2}, isRetry - true, status - Active. Dont auto pay for PaymentMethodId - {3}, return ...", new Object[]{subscriber.getPaymentId(), subscriber.getUserId(), subscriber.getEquipmentType(), createPayment.getPaymentMethodId()});
                this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "stop", "wait");
                return;
            }
        }

        // Сначала проверяем, что подписку не отменили (включен автоплатеж)
        // Если подписка была отменена:
        // 1. Закрываем подписку (проставляем статус 'Expired')
        // 2. Удаляем пользователя из рассылки в Redis
        // 3. Если подписку отменили ДО ПЕРВОГО автоплатежа, то сообщаем, что подписка закончилась
        // 3a. Если подписку отменили при повторных автоплатежах, то дополнительно ничего не сообщаем пользователю
        // 3a. Также в таблице ErrorPayments проставляем платежу статус 'stop', чтобы не было повторых автоплатежей
        if (!subscriber.isAutoPay()) {
            logger.log(Level.SEVERE, "Subscriber - {0} is unsubscribe - don't create auto payment ...", subscriber.getUserId());
            logger.log(Level.SEVERE, "Setting Expired status for subscriber - {0} with paymentId - {1} and expiredDate - {2}", new Object[]{subscriber.getUserId(), paymentId, dateFormat.format(billDate.getTime())});
            // Берем ID платежа из объекта Subscriber (subscriber.getPaymentId()), так как подписка с ID родительского платежа уже может быть закрыта
            // и получается, что мы повторно будем закрывать одну и ту же подписку
            subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), subscriber.getPaymentId(), dateFormat.format(billDate.getTime()), true);
            // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы, пока не подпишется снова
            redisEntity.remElement(subscriber.getEquipmentType(), subscriber.getUserId());

            // Если статус подписки НЕ AutoPay, то сообщаем дополнительно пользователю, что его подписка закончилась
            // То есть если при повторном автоплатеже окажется, что пользователь уже отменил подписку (отключил автоплатеж, чтобы не было повторного списания),
            // то дополнительно ничего не отправляем
            if (subscriber.getStatus() != null
                    && !subscriber.getStatus().equals("AutoPay"))
                general.sayHelloAfterUnsubscribe(subscriber.getUserId(), subscriber.getEquipmentType());

            // Если это был повторный автоплатеж, то проставляем в таблице ErrorPayments этому платежу статус "stop",
            // чтобы больше не брать его в обработку
            if (subscriber.getStatus() != null
                    && subscriber.getStatus().equals("AutoPay")) {
                this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "stop", "wait");
            }

            cleanUp(subscriber.getUserId());
            return;
        }

        // Обновляем статус текущей подписки на AutoPay
        subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), subscriber.getPaymentId());
        // Удаляем все флажки после смены статуса подписки на Expired
        cleanUp(subscriber.getUserId());

        logger.log(Level.SEVERE, "Send create autoPayment ...");

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        Gson gson = gsonBuilder.create();

        logger.log(Level.SEVERE, "Create autoPayment - {0}", gson.toJson(createPayment));

        // Отправляем данные на создание платежа в ЮKassa
        String authorization = Credentials.basic(System.getenv("shopId"), System.getenv("secret_key"));
        RequestBody body = RequestBody.create(gson.toJson(createPayment), MediaType.parse("application/json"));
        String createPaymentResponse = null;
        Request request = new Request.Builder()
                .url("https://api.yookassa.ru/v3/payments")
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .header("Idempotence-Key", UUID.randomUUID().toString())
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            logger.log(Level.SEVERE, "POST response status is - {0}", response.code());

            if (response.body() != null) {
                createPaymentResponse = response.body().string();
                logger.log(Level.SEVERE, "createPaymentResponse - {0}", createPaymentResponse);
            }

            // Сохраняем JSON запрос в БД для переповтора автоплатежа
            // Здесь выполняется запись ошибочного платежа в БД и дальнейший его повтор
            // Также в канал поддержки придет соответствующее сообщение
            // HTTP код НЕ 200 - это именно непредвиденная ошибка при автоплатеже
            if (response.code() != 200) {
                logger.log(Level.SEVERE, "Unexpected error for auto payment with payment method id - {0}, Error - {1}\nAdd payment info to DB and retry it ", new Object[]{createPayment.getPaymentMethodId(), createPaymentResponse});
                paymentOperations.addErrorPayment(createPayment.getPaymentMethodId(), gson.toJson(createPayment), true, subscriber.getUserId(), subscriber.getSubscriptionType(), "error");
                general.sendMessageAboutAutoPaymentError(subscriber.getUserId(), subscriber.getEquipmentType());
                admin.sendErrorMessagesToTechChannel("❗️Начали падать автоплатежи! Что-то пошло не так!\n".concat("Json response - " + createPaymentResponse));
                return;
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }

        ru.builder.model.payment.Payment payment = null;

        try {
            payment = gson.fromJson(createPaymentResponse, ru.builder.model.payment.Payment.class);
            logger.log(Level.SEVERE, "Payment - {0}", gson.toJson(payment));
        } catch (JsonSyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        if (payment == null) {
            logger.log(Level.SEVERE, "Payment object is null, return ...");
            return;
        }

        // Если статус canceled - фиксируем в БД информацию о причине и оповещаем пользователя
        // Также выполняем повтор автоплатежа: через 2 дня и через 5 дней
        if (payment.getStatus().equals("canceled")) {
            // Получаем все ошибочные платежи в статусе "wait" и проверяем, есть ли среди них
            // ID платежа, который сейчас завершился с ошибкой
            List<CreatePayment> waitPayments = paymentOperations.getErrorPaymentsByStatus("wait");
            if (waitPayments != null
                    && !waitPayments.isEmpty()) {

                // Если это был повторный автоплатеж (1-ая попытка), то устанавливаем ему tryCount = 2,
                // оповещаем пользователя и завершаем работу метода
                if (waitPayments.stream().anyMatch(elem -> elem.getPaymentMethodId().equals(createPayment.getPaymentMethodId()) && elem.getTryCount() == 1)) {
                    logger.log(Level.SEVERE, "After first error autoPayment: Set canceled status for error autoPayment - {0}", createPayment.getPaymentMethodId());
                    this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "canceled", "wait");
                    logger.log(Level.SEVERE, "After first error autoPayment: Set retry count value = 2 for error autoPayment - {0}", createPayment.getPaymentMethodId());
                    this.paymentOperations.setRetryCountForErrorAutoPayment(createPayment.getPaymentMethodId(), 1);

                    general.sayAutoPaymentError(subscriber.getUserId(),
                            "❗️К сожалению, автоплатеж по Вашей карте не прошел!\n\n" +
                                    "<b>Причина:</b> " + payment.getCancellationDetails().getReason() + "\n\n" +
                                    "<b>Я предприму еще одну попытку через 3 дня.</b>\n\n" +
                                    "ℹ️Вы можете отменить текущую подписку, чтобы не было повторного списания средств.\n" +
                                    "Для этого нажмите на кнопку <b>УПРАВЛЕНИЕ ПОДПИСКАМИ</b> и выберите соответствующую спецтехнику");
                    return;
                }

                // Если же это был повторный автоплатеж (2-ая попытка), то устанавливаем ему status = 'failed',
                // для подписки проставляем статус 'Expired', удаляем пользователя из рассылки в Redis,
                // оповещаем пользователя и завершаем работу метода
                else if (waitPayments.stream().anyMatch(elem -> elem.getPaymentMethodId().equals(createPayment.getPaymentMethodId()) && elem.getTryCount() == 2)) {
                    logger.log(Level.SEVERE, "After second error autoPayment: Set Expired status for subscriber - {0} with paymentId - {1}", new Object[]{subscriber.getUserId(), subscriber.getPaymentId()});
                    // В БД проставляем статус 'Expired' для подписки текущей датой
                    subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), subscriber.getPaymentId(), dateFormat.format(Calendar.getInstance().getTime()), true);

                    // Из рассылки в Redis не удаляем и создаем НОВУЮ Trial подписку
                    // Таким образом пользователь останется в рассылке, будет видеть подписку
                    // При нажатии на кнопку "Управление подписками" и сможет ее отменить
                    logger.log(Level.SEVERE, "After second error autoPayment: create Trial subscription for userId - {0} and equipmentType - {1}", new Object[]{subscriber.getUserId(), subscriber.getEquipmentType()});
                    subscriberOperations.addSubscriber(new Subscriber(subscriber.getUserId(), subscriber.getEquipmentType(), 0));

                    logger.log(Level.SEVERE, "After second error autoPayment: Set failed status for second error autoPayment - {0}", createPayment.getPaymentMethodId());
                    this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "failed", "wait");

                    general.sayAutoPaymentError(subscriber.getUserId(),
                            "❗️К сожалению, автоплатеж по Вашей карте не прошел!\n\n" +
                                    "<b>Причина:</b> " + payment.getCancellationDetails().getReason() + "\n\n" +
                                    "<b>Для возможности звонить заказчикам Вам необходимо оформить платную подписку заново.</b>");
                    return;
                }
            }

            logger.log(Level.SEVERE, "Payment status is canceled - {0}", gson.toJson(payment));
            // Записываем данные об ошибочном автоплатеже в БД в таблицу ErrorPayments
            paymentOperations.addErrorPayment(createPayment.getPaymentMethodId(),
                    gson.toJson(createPayment),
                    true,
                    subscriber.getUserId(),
                    subscriber.getSubscriptionType(),
                    "canceled");

            // Оповещаем пользователя, что автоплатеж не прошел и будет выполнено еще две попытки: через 2 дня и через 5 дней
            general.sendMessageAboutCanceledAutoPayment(subscriber.getUserId(), payment.getCancellationDetails().getReason());
            return;
        }

        // Перестраховка.
        // Если при автоплатеже получаем статус "pending" - значит это некорректная работа системы ЮKassa.
        // Заменяем статус платежа на "succeeded" и в дату CapturedAt записываем дату CreatedAt.
        // Также в технический канал сообщаем о происшествии!
        if (payment.getStatus().equals("pending")) {
            admin.sendErrorMessagesToTechChannel("❗При автоплатеже вернулся статус 'pending' и отсутствует поле CapturedAt.\nЭто некорректная работа системы ЮKassa.\nМеняем статус на 'succeeded' и в дату CapturedAt записываем дату CreatedAt.\npaymentId - " + payment.getId());
            payment.setStatus("succeeded");
            payment.setCapturedAt(payment.getCreatedAt());
        }

        // Получаем все ошибочные платежи в статусе "wait" и проверяем, есть ли среди них
        // ID платежа, который сейчас завершился успешно
        List<CreatePayment> waitPayments = paymentOperations.getErrorPaymentsByStatus("wait");
        if (waitPayments != null
                && !waitPayments.isEmpty()) {
            // Если это был повторный автоплатеж (1-ая или 2-ая попытка), то устанавливаем ему status = 'success',
            // чтобы не обрабатывать его повторно и продолжаем работу метода
            if (waitPayments.stream().anyMatch(elem -> elem.getPaymentMethodId().equals(createPayment.getPaymentMethodId())
                    && (elem.getTryCount() == 1 || elem.getTryCount() == 2))) {
                this.paymentOperations.setNewStatusForErrorAutoPayment(createPayment.getPaymentMethodId(), "success", "wait");
            }
        }

        payment.setUserId(subscriber.getUserId());

        Calendar nextBillCalendar = null;
        if (payment.getCapturedAt() != null && convertDate(payment.getCapturedAt()) != null) {
            nextBillCalendar = Calendar.getInstance();
            nextBillCalendar.setTime(convertDate(payment.getCapturedAt()));

            Subscription subscription = subscriptionOperations.getSubscriptionInfo(subscriber.getSubscriptionType(), subscriber.getEquipmentType());

            if (subscriber.getSubscriptionType().equals(SubscriptionType.week.value())) {
                nextBillCalendar.add(Calendar.WEEK_OF_MONTH, subscription.getDuration());
            } else if (subscriber.getSubscriptionType().equals(SubscriptionType.month.value())) {
                nextBillCalendar.add(Calendar.MONTH, subscription.getDuration());
            } else if (subscriber.getSubscriptionType().equals(SubscriptionType.year.value())) {
                nextBillCalendar.add(Calendar.YEAR, subscription.getDuration());
            } else if (subscriber.getSubscriptionType().equals(SubscriptionType.test.value())) {
                nextBillCalendar.add(Calendar.MINUTE, subscription.getDuration());
            }
            String nextBillDate = dateFormat.format(nextBillCalendar.getTime());
            logger.log(Level.INFO, "NextBillDate for auto payment - {0} is - {1}", new Object[]{payment.getId(), nextBillDate});
            payment.setNextBillDate(nextBillDate);
        } else {
            logger.log(Level.SEVERE, "Calendar is null for CapturedAt - {0}. Don't add info about next payment for auto payment with paymentId - {1}", new Object[]{payment.getCapturedAt(), payment.getId()});
            admin.sendErrorMessagesToTechChannel("❗️Что-то пошло не так!\nУ платежа отсутствует поле CapturedAt или не удалось привести дату к необходимому формату!".concat("\nJson response - " + createPaymentResponse));
        }

        //Если автоплатеж прошел успешно, то закрываем старую подписку текущей датой
        logger.log(Level.SEVERE, "Payment is SUCCEEDED: Set Expired status for subscriber - {0} with paymentId - {1}", new Object[]{subscriber.getUserId(), subscriber.getPaymentId()});
        subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), subscriber.getPaymentId(), dateFormat.format(Calendar.getInstance().getTime()), true);

        // Добавляем в таблицу Payments новый платеж
        paymentOperations.addAutoPayment(payment, subscriber.getUserId());

        // Добавляем в таблицу Subscribers новую строчку с подпиской в статусе 'Active'
        Subscriber subscriberAfterAutoPayment = new Subscriber(
                subscriber.getUserId(),
                payment.getId(),
                subscriber.getEmail(),
                subscriber.getSubscriptionType(),
                subscriber.getEquipmentType(),
                true
        );
        subscriberOperations.addSubscriberAfterAutoPayment(subscriberAfterAutoPayment, payment.getCapturedAt());

        // Сообщаем пользователю, что автоплатеж прошел успешно
        general.sendMessageAboutSuccessfullyAutoPayment(subscriberAfterAutoPayment.getUserId(), subscriber.getEquipmentType());

        // Если удалось установить дату следующего платежа, то создаем таску на следующий автоплатеж
        if (nextBillCalendar != null
                && payment.getNextBillDate() != null
                && !payment.getNextBillDate().isEmpty()) {
            this.createAutoPayment(payment, nextBillCalendar);
        }
    }

    private void cleanUp(String chatId) {
        redisEntity.deleteElement(chatId.concat("_subscription"));
    }

    private InlineKeyboardMarkup getPaymentButton(String payUrl) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("💰Оплатить");
        inlineKeyboardButton.setUrl(payUrl);
        inlineKeyboardButtons.add(inlineKeyboardButton);

        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        listOfListInlineKey.add(inlineKeyboardButtons);
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private Date convertDate(String utcDate) {

        logger.log(Level.INFO, "Input utcDate - {0}", utcDate);
        if (utcDate == null) {
            logger.log(Level.INFO, "Don't converting date, because value is null...");
            return null;
        }

        try {
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = utcFormat.parse(utcDate);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));

            Date europeDate = dateFormat.parse(dateFormat.format(date));
            logger.log(Level.INFO, "Converted Date - {0}", europeDate);

            return europeDate;
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
