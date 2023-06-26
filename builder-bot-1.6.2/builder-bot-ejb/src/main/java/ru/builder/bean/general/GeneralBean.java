package ru.builder.bean.general;

import com.google.gson.Gson;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.builder.bean.firebase.Firebase;
import ru.builder.bot.BuilderBot;
import ru.builder.db.connection.ConnectionOperations;
import ru.builder.db.dictionary.DictionaryOperations;
import ru.builder.db.document.DocumentOperations;
import ru.builder.db.order.OrderInfoOperations;
import ru.builder.db.payment.PaymentOperations;
import ru.builder.db.subscriber.SubscriberOperations;
import ru.builder.db.subscription.SubscriptionOperations;
import ru.builder.model.document.Document;
import ru.builder.model.order.*;
import ru.builder.model.payment.Payment;
import ru.builder.model.payment.error.ErrorPayment;
import ru.builder.model.subscr.Subscriber;
import ru.builder.model.subscr.Subscription;
import ru.builder.model.subscr.SubscriptionType;
import ru.builder.redis.RedisEntity;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.jms.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class GeneralBean implements General {

    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Inject
    private Firebase firebase;
    @Inject
    private DocumentOperations documentOperations;
    @Inject
    private DictionaryOperations dictionaryOperations;
    @Inject
    private OrderInfoOperations orderInfoOperations;
    @Inject
    private SubscriptionOperations subscriptionOperations;
    @Inject
    private SubscriberOperations subscriberOperations;
    @Inject
    private ConnectionOperations connectionOperations;
    @Inject
    private RedisEntity redisEntity;
    @Inject
    private PaymentOperations paymentOperations;

    @Inject
    @JMSConnectionFactory("java:/QueueConnectionFactory")
    private JMSContext context;
    @Resource(mappedName = "java:jboss/exported/jms/queue/ordersQueue")
    private Destination ordersQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/secondQueue")
    private Destination secondQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/thirdQueue")
    private Destination thirdQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/fourthQueue")
    private Destination fourthQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/fifthQueue")
    private Destination fifthQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/sixthQueue")
    private Destination sixthQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/seventhQueue")
    private Destination seventhQueue;
    @Resource(mappedName = "java:jboss/exported/jms/queue/eighthQueue")
    private Destination eighthQueue;

    @Override
    public void sendStartMessage(String userId) {

        boolean isAdmin = redisEntity.getElements("builder_admins") != null
                && !redisEntity.getElements("builder_admins").isEmpty()
                && redisEntity.getElements("builder_admins").contains(userId);

        if (isAdmin)
            redisEntity.setElement(userId.concat("_isAdmin"), "true");

        try {
            String text = "Привет! Я бот Строитель! 🚜\n\n" +
                    "Здесь вы сможете получать заказы на спецтехнику по подписке. Бот работает по Москве и МО.\n" +
                    "Для размещения заказов в боте воспользуйтесь нашим Android приложением [Строитель](https://play.google.com/store/apps/details?id=com.builders)\n\n" +
                    "*Как работает бот:*\n" +
                    "1. Нажмите на кнопку *Выбрать спецтехнику*\n" +
                    "2. Выберите спецтехнику, на которую хотите получать заказы и нажмите на нее\n\n" +
                    "Готово! Теперь вы будете получать заказы на выбранную спецтехнику.\n" +
                    "Когда закончатся бесплатные просмотры номеров телефонов - бот сам предложит вам оформить платную подписку. Просто следуйте инструкции.\n\n" +
                    "*Кнопки:*\n" +
                    "⏺️*Выбрать спецтехнику* - Выбор спецтехники, на которую необходимо получать заказы\n" +
                    "⏺️*Управление подписками* - Информация по текущим подпискам: Тип подписки, кол-во бесплатных просмотров, дата следующего платежа и тд\n" +
                    "⏺️*Написать нам* - Обращение в поддержку, если у вас возникли вопросы \n" +
                    "⏺️*Помощь* - Информация по работе бота и стоимости подписки";

            new BuilderBot().execute(SendMessage.builder()
                    .chatId(userId)
                    .text(text
                            .replaceAll("\\!", "\\\\!")
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("\\-", "\\\\-")
                            .replaceAll("\\=", "\\\\=")
                    )
                    .parseMode(ParseMode.MARKDOWNV2)
                    .disableWebPagePreview(true)
                    .replyMarkup(getMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }

        logger.log(Level.SEVERE, "Restoring subscriptions for userId - {0}", userId);

        // ==> Восстанавливаем пробные подписки для пользователя после возвращения в бота
        // Получаем список всех подписок пользователя, у которых статус 'Pause'
        List<Subscriber> pauseSubscribers = subscriberOperations.getPauseSubscriptions(userId);

        // Если такие подписки есть, то обрабатываем их
        if (pauseSubscribers != null && !pauseSubscribers.isEmpty()) {
            // Пробегаемся по всем подпискам
            pauseSubscribers.forEach(pauseSubscriber -> {
                // Проставляем каждой подписке в БД статус 'Trial'
                subscriberOperations.restoreTrialStatusForSubscription(pauseSubscriber);
                // Возвращаем пользователя в рассылку (в Redis) для текущего типа спецтехники
                this.redisEntity.pushElement(pauseSubscriber.getEquipmentType(), userId);
            });
            logger.log(Level.SEVERE, "Restored {0} trial subscriptions for userId - {1}", new Object[]{pauseSubscribers.size(), userId});
        }

        // ==> Восстанавливаем платные подписки для пользователя после возвращения в бота
        // Получаем все активные (платные) подписки, которые есть
        List<Subscriber> activeSubscribers = this.subscriberOperations.getAllActiveSubscribers();
        if (activeSubscribers != null && !activeSubscribers.isEmpty()) {
            // Удаляем из списка все подписки, которые не относятся к текущему пользователю
            activeSubscribers.removeIf(elem -> !elem.getUserId().equals(userId));
            // Пробегаемся по всем подпискам
            activeSubscribers.forEach(activeSubscriber -> {
                // Возвращаем пользователя в рассылку (в Redis) для текущего типа спецтехники
                this.redisEntity.pushElement(activeSubscriber.getEquipmentType(), userId);
            });
            logger.log(Level.SEVERE, "Restored {0} paid subscriptions for userId - {1}", new Object[]{activeSubscribers.size(), userId});
        }
    }

    @Override
    public void sendLostUserMessage(String userId) {
        try {
            String text = "Привет! Я бот Строитель! 🚜\n\n" +
                    "Здесь вы сможете получать заказы на спецтехнику по подписке. Бот работает по Москве и МО.\n" +
                    "Для размещения заказов в боте воспользуйтесь нашим Android приложением [Строитель](https://play.google.com/store/apps/details?id=com.builders)\n\n" +
                    "*Как работает бот:*\n" +
                    "1. Нажмите на кнопку *Выбрать спецтехнику*\n" +
                    "2. Выберите спецтехнику, на которую хотите получать заказы и нажмите на нее\n\n" +
                    "Готово! Теперь вы будете получать заказы на выбранную спецтехнику.\n" +
                    "Когда закончатся бесплатные просмотры номеров телефонов - бот сам предложит вам оформить платную подписку. Просто следуйте инструкции.\n\n" +
                    "*Кнопки:*\n" +
                    "⏺️*Выбрать спецтехнику* - Выбор спецтехники, на которую необходимо получать заказы\n" +
                    "⏺️*Управление подписками* - Информация по текущим подпискам: Тип подписки, кол-во бесплатных просмотров, дата следующего платежа и тд\n" +
                    "⏺️*Написать нам* - Обращение в поддержку, если у вас возникли вопросы \n" +
                    "⏺️*Помощь* - Информация по работе бота и стоимости подписки";

            new BuilderBot().execute(SendMessage.builder()
                    .chatId(userId)
                    .text(text
                            .replaceAll("\\!", "\\\\!")
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("\\-", "\\\\-")
                            .replaceAll("\\=", "\\\\=")
                    )
                    .parseMode(ParseMode.MARKDOWNV2)
                    .disableWebPagePreview(true)
                    .replyMarkup(getMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendMainMenuMessage(String userId) {
        String text = "Добро пожаловать в Главное Меню!🚜\n\n" +
                "*Кнопки:*\n" +
                "⏺️*Выбрать спецтехнику* - Выбор спецтехники, на которую необходимо получать заказы\n" +
                "⏺️*Управление подписками* - Информация по текущим подпискам: Тип подписки, кол-во бесплатных просмотров, дата следующего платежа и тд\n" +
                "⏺️*Написать нам* - Обращение в поддержку, если у вас возникли вопросы \n" +
                "⏺️*Помощь* - Информация по работе бота и стоимости подписки\n\n" +
                "*Выберите необходимый пункт меню*";

        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(userId)
                    .text(text
                            .replaceAll("\\!", "\\\\!")
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("\\-", "\\\\-")
                            .replaceAll("\\=", "\\\\=")
                    )
                    .parseMode(ParseMode.MARKDOWNV2)
                    .replyMarkup(getOptionButtons(userId))
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getNewOrders() {
        logger.log(Level.SEVERE, "START getNewOrders ...");

        List<Document> orders = firebase.getOrderInfo();

        if (orders == null) {
            logger.log(Level.SEVERE, "orders is null! STOP getNewOrders transaction ...");
            return;
        }

        orders.forEach(document -> {

            // Сначала проверяем наличие номера телефона в заказе.
            // Если номер телефона отсутствует, то пропускаем такой заказ.
            if (document.getFields().getUserPhone().getStringValue() == null || document.getFields().getUserPhone().getStringValue().isEmpty()) {
                logger.log(Level.SEVERE, "Skipping documentId - {0}, because phone number is absent", document.getFields().getId().getStringValue());
                return;
            }

            // Если в заказе отсутствует комментарий - пропускаем такой заказ
            if (document.getFields().getComment().getStringValue() == null || document.getFields().getComment().getStringValue().isEmpty()) {
                logger.log(Level.SEVERE, "Skipping documentId - {0}, because Comment is null", document.getFields().getId().getStringValue());
                return;
            }

            // ==> Делаем перестраховку от повторок на всякий случай:
            // Если дата заказа (createdAt) меньше даты последнего заказа в БД бота (lastOrderTime в Redis) - пропускаем этот заказ
            if (redisEntity.getElement("lastOrderTime") != null) {
                long lastOrderTime = Long.parseLong(redisEntity.getElement("lastOrderTime"));
                long currentOrderTime = Timestamp.valueOf(document.getFields().getCreatedAt().getTimestampValue()).getTime();
                if (currentOrderTime < lastOrderTime) {
                    logger.log(Level.SEVERE, "currentOrderTime - {0} < lastOrderTime - {1}, skip document - {2}", new Object[]{currentOrderTime, lastOrderTime, document.getFields().getId().getStringValue()});
                    return;
                }
            }

            String documentId = document.getFields().getId().getStringValue();
            // Если в БД уже есть такой ID документа, то пропускаем его
            if (documentOperations.checkDocumentId(documentId)) {
                return;
            }
            // Сохраняем в БД новый документ
            documentOperations.addDocument(document);
        });
        logger.log(Level.SEVERE, "STOP getNewOrders ...");
    }

    @Override
    public void putOrdersToQueue() {
        logger.log(Level.SEVERE, "START putOrdersToQueue ...");

        // Получаем первые два документа из БД, которые НЕ обработаны
        // Запрос делаем с сортировкой "от старых к новым", чтобы соблюсти порядок отправки
        List<Document> orders = this.documentOperations.getFirstTwoNotProcessedDocuments();
        // Получаем из БД (таблица order_info) все ID документов, которые были отправлены хотя бы одному пользователю
        List<String> uniqueDocumentIds = this.orderInfoOperations.getAllUniqueDocumentIds();
        // Удаляем из списка заказов те, которые уже были отправлены хотя бы одному пользователю
        if (uniqueDocumentIds != null && !uniqueDocumentIds.isEmpty())
            orders.removeIf(order -> uniqueDocumentIds.contains(order.getFields().getId().getStringValue()));

        // Получаем из БД Firebase все виды спецтехники
        // В дальнейшем по ID будем получать имя спецтехники из этого списка, чтобы не дергать постоянно Firebase!
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();

        orders.forEach(document -> {
            String documentId = document.getFields().getId().getStringValue();

            // Помечаем документ как обработанный
            this.documentOperations.markDocumentAsProcessed(documentId);

            // ===> Достаем все необходимые поля для отправки заказа
            String equipmentId = document.getFields().getTypeID().getStringValue();
            // Достаем имя спецтехники по ее ID из списка heavyTypes
            Document doc = heavyTypes.stream().filter(heavyType -> heavyType.getFields().getId().getStringValue().equals(equipmentId)).findFirst().orElse(null);
            String equipmentName = doc != null ? doc.getFields().getName().getStringValue() : "";

            String latitude = document.getFields().getLocation().getGeoPoint().getLatitude();
            String longitude = document.getFields().getLocation().getGeoPoint().getLongitude();
            String google_maps = "<a href=\"".concat("https://www.google.com/maps/place/").concat(latitude).concat(",").concat(longitude).concat("\">")
                    .concat("Google карты")
                    .concat("</a>");
            String yandex_maps = "<a href=\"".concat("https://maps.yandex.ru/?text=").concat(latitude).concat(",").concat(longitude).concat("\">")
                    .concat("Яндекс карты")
                    .concat("</a>");
            String mapInfo = yandex_maps.concat("\n").concat(google_maps);
            String paymentMethod;
            switch (document.getFields().getPaymentMethod().getStringValue()) {
                case "bank":
                    paymentMethod = "Безнал";
                    break;
                case "cash":
                    paymentMethod = "Нал";
                    break;
                default:
                    paymentMethod = "Любой";
                    break;
            }

            // Получаем список пользователей, которые подписаны на текущий тип спецтехники
            List<String> userIds = redisEntity.getElements(equipmentId);
            if (userIds == null || userIds.isEmpty())
                return;

            logger.log(Level.INFO, "users - {0} for typeId - {1}", new Object[]{Arrays.toString(userIds.toArray()), equipmentId});

            ObjectMessage msg = context.createObjectMessage();
            JMSProducer producer = context.createProducer();

            // Формируем структуру заказа для пользователя
            Order order = new Order(documentId,
                    document.getFields().getCreatedAt().getTimestampValue(),
                    document.getFields().getComment().getStringValue(),
                    paymentMethod,
                    new Equipment(equipmentId, equipmentName),
                    new User(document.getFields().getUserName().getStringValue(), document.getFields().getUserPhone().getStringValue()),
                    new Location(document.getFields().getAddress().getStringValue(), mapInfo));

            userIds.forEach(id -> {
                // ===> Отправляем заказ в очередь на обработку
                try {
                    msg.setStringProperty("userId", id);
                    msg.setObject(order);
                    int queueId = new Random().nextInt(8);

                    if (queueId == 0) {
                        logger.log(Level.INFO, "Send message to ordersQueue ...");
                        producer.send(ordersQueue, msg);
                    } else if (queueId == 1) {
                        logger.log(Level.INFO, "Send message to secondQueue ...");
                        producer.send(secondQueue, msg);
                    } else if (queueId == 2) {
                        logger.log(Level.INFO, "Send message to thirdQueue ...");
                        producer.send(thirdQueue, msg);
                    } else if (queueId == 3) {
                        logger.log(Level.INFO, "Send message to fourthQueue ...");
                        producer.send(fourthQueue, msg);
                    } else if (queueId == 4) {
                        logger.log(Level.INFO, "Send message to fifthQueue ...");
                        producer.send(fifthQueue, msg);
                    } else if (queueId == 5) {
                        logger.log(Level.INFO, "Send message to sixthQueue ...");
                        producer.send(sixthQueue, msg);
                    } else if (queueId == 6) {
                        logger.log(Level.INFO, "Send message to seventhQueue ...");
                        producer.send(seventhQueue, msg);
                    } else if (queueId == 7) {
                        logger.log(Level.INFO, "Send message to eighthQueue ...");
                        producer.send(eighthQueue, msg);
                    }

                } catch (JMSException e) {
                    logger.log(Level.SEVERE, null, e.getMessage());
                }
            });
        });
        logger.log(Level.SEVERE, "STOP putOrdersToQueue ...");
    }

    @Override
    public void sendOrderToUser(Order order, String userId) {
        logger.log(Level.INFO, "START sendOrderToUser for documentId - {0} and userId - {1} ...", new Object[]{order.getDocumentId(), userId});
        // Для новых заказов после приобретения подписки (при активной подписке) номер телефона будет сразу в открытом виде
        // ТАКЖЕ заказы с открытым номером телефона будут приходить пользователям, которые ожидают повторный автоплатеж
        // Пример: Пользователь пополнил баланс, но ему для списания ждать 2/3 дня, поэтому он продолжает получать заказы с открытым номером телефона
        // В метод checkIsActiveSubscriber добавлена также проверка на статус 'AutoPay'
        boolean isActiveSubscr = subscriberOperations.checkIsActiveSubscriber(userId, order.getEquipment().getId());

        // Проверяем, является ли пользователь админом
        boolean isAdmin = redisEntity.getElements("builder_admins") != null
                && !redisEntity.getElements("builder_admins").isEmpty()
                && redisEntity.getElements("builder_admins").contains(userId);

        String user = order.getUser().getName()
                .replaceAll("<", "")
                .replaceAll(">", "");

        String comment = order.getComment()
                .replaceAll("<", "")
                .replaceAll(">", "");

        String additionalText = "<b>Комментарий:</b> " + comment + "\n\n";
        if (isAdmin) {
            additionalText = "<b>Комментарий:</b> " + comment + "\n" +
                    "<b>Адрес:</b> " + order.getLocation().getAddress() + "\n\n";
        }

        String text = "<b>Заказ на " + order.getEquipment().getName() + "</b>\n\n" +
                "<b>Дата:</b> " + order.getCreatedAt() + "\n" +
                "❗️Чем больше времени прошло с момента появления заказа, тем больше вероятность, что он уже закрыт\n" +
                "<b>Способ оплаты:</b> " + order.getPaymentMethod() + "\n" +
                "<b>Пользователь:</b> " + user + "\n\n" +
                additionalText +
                "<b>Показать на картах:</b> " + "\n" + order.getLocation().getMapInfo() + "\n" +
                "<b>Местоположение заказа может быть указано неверно, уточните его у заказчика\n</b>" +
                (isActiveSubscr ? "\n<b>Номер телефона:</b>" + order.getUser().getPhone().concat("\n\n❗️Обратите внимание, чем больше времени прошло с момента появления заказа, тем больше вероятность, что он уже закрыт.") : "");

        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(text)
                .replyMarkup(getShowPhoneButton(isActiveSubscr))
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .build();
        try {
            Message message = new BuilderBot().execute(sendMessage);
            // Сохраняем маппинг chatId + documentId + messageId
            orderInfoOperations.addTmpInfo(new OrderInfo(userId, order.getDocumentId(), String.valueOf(message.getMessageId())), order.getCreatedAt());
        } catch (TelegramApiException ex) {

            if (ex.getMessage().equals("Unable to execute sendMessage method")) {
                logger.log(Level.SEVERE, "ExceptionMessage - {0}, Repeat sending for userId - {1}", new Object[]{ex.getMessage(), userId});
                executorService.schedule(() -> this.sendOrderToUser(order, userId), 10, TimeUnit.SECONDS);
                return;
            }

            if (ex instanceof TelegramApiRequestException) {
                if (((TelegramApiRequestException) ex).getErrorCode() != null) {

                    if (((TelegramApiRequestException) ex).getErrorCode() == 403
                            && (ex.getMessage().contains("blocked by the user")
                            || ex.getMessage().contains("user is deactivated"))) {
                        logger.log(Level.SEVERE, "ErrorCode 403 - {0}. Removing the userId - {1} from Redis and canceling Trial subscription for subscriber... ", new Object[]{ex.getMessage(), userId});
                        // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы
                        redisEntity.remElement(order.getEquipment().getId(), userId);
                        // В БД проставляем статус для текущей подписки 'Pause'
                        // Таким образом сможем восстановить эту подписку при возвращении в бота
                        subscriberOperations.cancelTrialSubscription(userId, order.getEquipment().getId(), true);
                        return;
                    }

                    if (((TelegramApiRequestException) ex).getErrorCode() == 429) {
                        logger.log(Level.SEVERE, "ErrorCode 429 - {0}. Create schedule task for repeat sending message: retry - {1} sec to userId - {2}", new Object[]{ex.getMessage(), ((TelegramApiRequestException) ex).getParameters().getRetryAfter(), userId});
                        executorService.schedule(() -> this.sendOrderToUser(order, userId), ((TelegramApiRequestException) ex).getParameters().getRetryAfter(), TimeUnit.SECONDS);
                        return;
                    }
                }
            }
            logger.log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected Error: ", ex);
        }
        logger.log(Level.INFO, "STOP sendOrderToUser for documentId - {0} and userId - {1} ...", new Object[]{order.getDocumentId(), userId});
    }

    @Override
    public void getChoiceHeavyMachineryTypes(String userId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text("Выберите тип спецтехники, по которой хотите получать заказы")
                .replyMarkup(getHeavyMachineryTypeButtons())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void sendMessageAboutChoice(String userId, String heavyMachineryType) {
        // Достаем из БД Firebase имя спецтехнки
        String heavyMachineryName = firebase.getHeavyMachineryNameById(heavyMachineryType);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text("❗️Теперь Вы будете получать новые заказы по спецтехнике <b>" + heavyMachineryName + "</b>")
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    /**
     * Отправка сообщения о новой стоимости подписки для Telegram
     
    @Override
    public void sendMessageAboutUpdateSubscriptionforTelegram(String userId) {
        // Получаем из БД информацию по Активной или Триальной подписке для пользователя
        Subscriber subscriberInfo = subscriberOperations.getSubscriberByIdAndEquipmentTypeAndActiveOrAutoPayStatus(userId, emailSubscriber);
        // Получаем из БД информацию по Типу спецтехники
        String text = """
            ❗️Уведомляем вас о новой стоимости автосписания по вашей подписке.
            Вы можете описаться от платной подписки в любой момент нажав на кнопку 'главное меню' затем 'управление подписками'
            """;
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }
    */

    private ReplyKeyboardMarkup getMainMenuButton() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(
                        Collections.singletonList(
                                new KeyboardButton("Главное меню 🎯")
                        )))
                .resizeKeyboard(true)
                .build();
    }

    private InlineKeyboardMarkup getOptionButtons(String userId) {

        boolean isAdmin = redisEntity.getElement(userId.concat("_isAdmin")) != null
                && !redisEntity.getElement(userId.concat("_isAdmin")).isEmpty()
                && redisEntity.getElement(userId.concat("_isAdmin")).equals("true");

        return InlineKeyboardMarkup.builder()
                .keyboardRow(Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text("Выбрать спецтехнику 🚜")
                                .callbackData("choose_machine")
                                .build()
                ))
                .keyboardRow(Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text("Управление подписками 📬")
                                .callbackData("sub_management")
                                .build()
                ))
                .keyboardRow(Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text("Написать нам ✏️")
                                .callbackData("support")
                                .build()
                ))
                .keyboardRow(Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text("Помощь ℹ️️")
                                .callbackData("help")
                                .build()
                ))
                .keyboardRow(isAdmin
                        ? Collections.singletonList(
                        InlineKeyboardButton.builder()
                                .text("Статистика 📊")
                                .callbackData("statistic")
                                .build())
                        : new ArrayList<>()
                )
                .build();
    }

    @Override
    public boolean checkSubscriptionForUserId(String userId, String heavyMachineryType) {
        // Получаем из БД информацию по Активной или Триальной подписке для пользователя
        Subscriber subscriberInfo = subscriberOperations.getActiveOrTrialSubscriberByIdAndEquipmentType(userId, heavyMachineryType);
        // Достаем из БД Firebase имя спецтехнки
        String heavyMachineryName = firebase.getHeavyMachineryNameById(heavyMachineryType);
        // Если у пользователя уже имеется подписка - то сообщаем ему об этом
        if (subscriberInfo != null) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(userId)
                    .text("❗️Вы уже подписаны на обновление по заказам для спецтехники <b>" + heavyMachineryName + "</b>")
                    .parseMode(ParseMode.HTML)
                    .build();
            try {
                new BuilderBot().execute(sendMessage);
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        return subscriberInfo != null;
    }

    private void sendMessageAboutTheOrderWasClosed(String userId, Integer messageId) {
        logger.log(Level.SEVERE, "Edit message (show_phone): userId - {0}, messageId - {1}", new Object[]{userId, messageId});
        try {
            new BuilderBot().execute(
                    EditMessageText.builder()
                            .chatId(Long.valueOf(userId))
                            .text("Срок давности заказа истек 😔")
                            .messageId(messageId)
                            .build()
            );
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, "Failed to edit old message - {0}", e.getMessage());
        }
    }

    @Override
    public void showPhoneNumber(String userId, Integer messageId, String text) {

        try {
            // По chatId и messageId получаем id документа
            String documentId = orderInfoOperations.getDocumentIdByChatIdAndMessageId(userId, String.valueOf(messageId));

            // Если в таблице с заказами для пользователя не было найдено записи,
            // значит этот заказ был автоматически или вручную удален из БД, так как уже неакутален.
            // Редактируем сообщение с заказом, говорим, что истек срок заказа
            if (documentId == null || documentId.isEmpty()) {
                this.sendMessageAboutTheOrderWasClosed(userId, messageId);
                return;
            }

            // По documentId достаем инфу по документу
            Document document = documentOperations.getDocumentById(documentId);
            if (document == null) {
                logger.log(Level.SEVERE, "Document with id - {0} is null!", documentId);
                return;
            }

            String equipmentId = document.getFields().getTypeID().getStringValue();
            int connectionCount = 0;

            // Получаем из Redis значение коннектов, если они имеются
            if (redisEntity.hashGet(userId, equipmentId.concat("_connectionCount")) != null
                    && !redisEntity.hashGet(userId, equipmentId.concat("_connectionCount")).isEmpty()) {
                logger.log(Level.SEVERE, "Get connectionCount value from Redis...");
                connectionCount = Integer.parseInt(redisEntity.hashGet(userId, equipmentId.concat("_connectionCount")));
            }

            // Если у пользователя есть в кэше (Redis) бесплатные клики по спецтехнике,
            // НО при этом актуальное кол-во кликов для спецтехники поменялось (например, спецтехника стала платной),
            // то задаем пользователю кол-во кликов равное актуальному на данный момент для текущей спецтехники
            // + в кэше (Redis) обновляем значение
            if (connectionCount != 0) {
                int actualConnectionCountByEquipmentType = connectionOperations.getCountOfConnectionsByEquipmentType(equipmentId);
                if (connectionCount > actualConnectionCountByEquipmentType) {
                    connectionCount = actualConnectionCountByEquipmentType;
                    redisEntity.hashSet(userId, equipmentId.concat("_connectionCount"), String.valueOf(connectionCount));
                }
            }

            // После приобретения подписки (при активной подписке) можно посмотреть номер телефона для старых заказов
            boolean isActiveSubscr = subscriberOperations.checkIsActiveSubscriber(userId, equipmentId);

            // Вытаскиваем долготу и широту
            String latitude = document.getFields().getLocation().getGeoPoint().getLatitude();
            String longitude = document.getFields().getLocation().getGeoPoint().getLongitude();

            String google_maps = "<a href=\"".concat("https://www.google.com/maps/place/").concat(latitude).concat(",").concat(longitude).concat("\">")
                    .concat("Google карты")
                    .concat("</a>");
            String yandex_maps = "<a href=\"".concat("https://maps.yandex.ru/?text=").concat(latitude).concat(",").concat(longitude).concat("\">")
                    .concat("Яндекс карты")
                    .concat("</a>");
            String mapInfo = yandex_maps.concat("\n").concat(google_maps);

            // Если нет активной подписки и у пользователя 0 коннектов, но он все равно попытается открыть номер, то выдаем сообщение, что сначала необходимо оформить подписку
            if (!isActiveSubscr && connectionCount == 0) {
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(userId)
                        .text("❗️К сожалению, у Вас закончился пробный период!\n" +
                                "Для возможности звонить заказчикам Вам необходимо оформить платную подписку для спецтехники <b>" + firebase.getHeavyMachineryNameById(equipmentId) + "</b>\n\n" +
                                "❗️Обращаю ваше внимание, что при оформлении платной подписки у вас будет автоматическое списание средств после окончания срока подписки.\n" +
                                "Для отмены автосписания средств нажмите на кнопку <b>УПРАВЛЕНИЕ ПОДПИСКАМИ</b> и выберите подписку, у которой хотите отменить автоплатеж.")
                        .replyMarkup(getSubscriptionButtons(equipmentId, userId))
                        .parseMode(ParseMode.HTML)
                        .build();
                try {
                    new BuilderBot().execute(sendMessage);
                } catch (TelegramApiException e) {
                    logger.log(Level.SEVERE, null, e);
                }
                return;
            }

            // Если для данной спецтехники у пользователя осталось более 20 коннектов, значит этот тип спецтехники бесплатный
            // Для нее НЕ отображаем кол-во коннектов при отображении номера.
            boolean isFreeSubscription = connectionCount > 20;

            String userPhone = document.getFields().getUserPhone().getStringValue();
            String message = text
                    .replaceAll("Заказ на", "<b>Заказ на</b>")
                    .replaceAll("Дата:", "<b>Дата:</b>")
                    .replaceAll("Способ оплаты:", "<b>Способ оплаты:</b>")
                    .replaceAll("Комментарий:", "<b>Комментарий:</b>")
                    .replaceAll("Адрес:", "<b>Адрес:</b>")
                    .replaceAll("Пользователь:", "<b>Пользователь:</b>")
                    .replaceAll("Показать на картах:", "<b>Показать на картах:</b>")
                    .replaceAll("Яндекс карты\nGoogle карты", mapInfo)
                    .replaceAll("Местоположение заказа может быть указано неверно, уточните его у заказчика", "<b>Местоположение заказа может быть указано неверно, уточните его у заказчика</b>")
                    .concat(!isActiveSubscr && !isFreeSubscription ? "\n\n<b>Номер телефона:</b> " + userPhone + "\n<b>Осталось бесплатных просмотров:</b> " + (connectionCount - 1) : "\n\n<b>Номер телефона:</b> " + userPhone)
                    .concat("\n\n❗️Обратите внимание, чем больше времени прошло с момента появления заказа, тем больше вероятность, что он уже закрыт.");

            try {
                new BuilderBot().execute(
                        EditMessageText.builder()
                                .chatId(userId)
                                .text(message)
                                .messageId(messageId)
                                .parseMode(ParseMode.HTML)
                                .disableWebPagePreview(true)
                                .replyMarkup(getShowMainMenuButton())
                                .build());
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
            // Если нет активной подписки, то обновляем в БД кол-во коннектов для подписчика
            if (!isActiveSubscr)
                subscriberOperations.changeConnectionCount(userId, equipmentId, connectionCount - 1);

            // Если нет активной подписки и остался последний коннект, то сразу после показа номера отправляем сообщение, что необходимо оформить подписку
            if (!isActiveSubscr && connectionCount - 1 == 0) {
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(userId)
                        .text("❗️У Вас закончился пробный период на получение заказов по спецтехнике <b>" + firebase.getHeavyMachineryNameById(document.getFields().getTypeID().getStringValue()) + "</b>\n\n" +
                                "Для возможности звонить заказчикам Вам необходимо оформить платную подписку.\n" +
                                "Выберите длительность подписки\n\n" +
                                "❗️Обращаю ваше внимание, что при оформлении платной подписки у вас будет автоматическое списание средств после окончания срока подписки.\n" +
                                "Для отмены автосписания средств нажмите на кнопку <b>УПРАВЛЕНИЕ ПОДПИСКАМИ</b> и выберите подписку, у которой хотите отменить автоплатеж\n" +
                                "Вы можете отписаться от платной подписки в любой момент нажав у заказа на кнопку <b>ГЛАВНОЕ МЕНЮ</b>, а затем <b>УПРАВЛЕНИЕ ПОДПИСКАМИ</b>.")
                        .replyMarkup(getSubscriptionButtons(equipmentId, userId))
                        .parseMode(ParseMode.HTML)
                        .build();
                try {
                    new BuilderBot().execute(sendMessage);
                } catch (TelegramApiException e) {
                    logger.log(Level.SEVERE, null, e);
                }
            }
            // Если нет активной подписки, то уменьшаем счетчик коннектов в переменной connectionCount в Redis
            // Таким образом, если пользователь отменил триальную подписку с 2 коннектами - он может возобновить ее сразу с 2 коннектами
            if (!isActiveSubscr)
                redisEntity.hashSet(userId, equipmentId.concat("_connectionCount"), String.valueOf(connectionCount - 1));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private InlineKeyboardMarkup getSubscriptionButtons(String equipmentId, String userId) {

        boolean isAdmin = redisEntity.getElements("builder_admins") != null
                && !redisEntity.getElements("builder_admins").isEmpty()
                && redisEntity.getElements("builder_admins").contains(userId);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();

        // Получаем из БД все типы подписок для текущей спецтехники и выводим соответствующие кнопки
        subscriptionOperations.getAllSubscriptions(equipmentId).forEach(subscription -> {
            if (subscription.getSubscriptionType() == SubscriptionType.test && !isAdmin)
                return;
            List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText(subscription.getDescription().concat(" ").concat(subscription.getAmount().concat(" руб.")));
            inlineKeyboardButton.setCallbackData(subscription.getSubscriptionType().value().concat("_").concat(equipmentId));
            inlineKeyboardButtons.add(inlineKeyboardButton);
            listOfListInlineKey.add(inlineKeyboardButtons);
        });
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    @Override
    public boolean checkActiveSubscription(String chatId, String equipmentType) {
        return subscriberOperations.checkActiveSubscription(chatId, equipmentType);
    }

    @Override
    public boolean checkTrialSubscription(String chatId, String equipmentType) {
        return subscriberOperations.checkTrialSubscription(chatId, equipmentType);
    }

    @Override
    public boolean checkAutoPaySubscription(String chatId, String equipmentType) {
        return subscriberOperations.checkAutoPaySubscription(chatId, equipmentType);
    }

    @Override
    public void sendAlreadyHaveSubscription(String chatId, String equipmentType) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("❗У Вас уже оформлена платная подписка на спецтехнику <b>" + firebase.getHeavyMachineryNameById(equipmentType).toUpperCase() + "</b>.\nВы можете отменить подписку, нажав на соответствующую кнопку👇🏻.\nОформить новую подписку Вы сможете только после окончания текущей.")
                .replyMarkup(getMainMenuButton())
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendHaveAutoPayment(String chatId, String equipmentType) {
        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❗В данный момент для платной подписки на спецтехнику <b>" + firebase.getHeavyMachineryNameById(equipmentType).toUpperCase() + "</b> выполняется автоплатеж.\n" +
                            "Вам необходимо либо дождаться результата выполнения автоплатежа, либо отписаться и заново оформить подписку")
                    .replyMarkup(getMainMenuButton())
                    .parseMode(ParseMode.HTML)
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendDontHaveTrialSubscription(String chatId, String equipmentType) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("❗Вы не можете оформить платную подписку на спецтехнику <b>" + firebase.getHeavyMachineryNameById(equipmentType).toUpperCase() + "</b>.\nСначала Вам необходимо подписаться на данную спецтехнику. Нажмите на кноку 'Выбрать спецтехнику'👇🏻")
                .replyMarkup(getMainMenuButton())
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendRequestEmail(String chatId) {
        logger.log(Level.INFO, "method - Request Email, userId - {0}", chatId);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("❗️Отправьте Ваш email адрес. Он является обязательным и необходим для отправки чека после оплаты подписки.\n\n" +
                        "<b>Ничего кроме чека на указанный адрес мы Вам отправлять не будем!</b>")
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void saveEmail(String chatId, String email) {
        redisEntity.hashSet(chatId, "email", email);
        redisEntity.deleteElement(chatId.concat("_email"));
    }

    @Override
    public void addSubscriberInfo(String chatId, String heavyMachineryType) {
        // По умолчанию задаем кол-во коннектов, которое соответствует выбранному типу спецтехники из БД
        int connectionCount = connectionOperations.getCountOfConnectionsByEquipmentType(heavyMachineryType);

        // Если в Redis имеется значение кол-ва коннектов, то создаем подписку с данным кол-во коннектов
        // Это необходимо, чтобы новая подписка имела остаточное кол-во коннектов, а не создавалась с максимально допступным кол-вом коннектов
        // Также проверяем, чтобы кол-во коннектов у пользователя было МЕНЬШЕ или РАВНО кол-ву коннектов по умолчанию из БД для текущей спецтехники.
        // Это необходимо для кейса, когда пользователь пользовался бесплатной подпиской, потом отменил ее, сохранив за собой большое кол-во коннектов,
        // но подписка стала платной с ограниченным кол-во коннектов и он в этот момент подписывается на нее.
        if (redisEntity.hashGet(chatId, heavyMachineryType.concat("_connectionCount")) != null
                && !redisEntity.hashGet(chatId, heavyMachineryType.concat("_connectionCount")).isEmpty()
                && Integer.parseInt(redisEntity.hashGet(chatId, heavyMachineryType.concat("_connectionCount"))) <= connectionCount) {
            connectionCount = Integer.parseInt(redisEntity.hashGet(chatId, heavyMachineryType.concat("_connectionCount")));
        }
        subscriberOperations.addSubscriber(new Subscriber(chatId, heavyMachineryType, connectionCount));
        redisEntity.hashSet(chatId, heavyMachineryType.concat("_connectionCount"), String.valueOf(connectionCount));
    }

    @Override
    public void getAllActiveAndTrialSubscriptionsForUser(String chatId) {
        // Получаем все подписки пользователя и предоставляем выбор
        // Если активных/триальных подписок нет, то сообщаем, что таковых нет

        List<Subscriber> resultSubscriberList = null;
        List<Subscriber> subscriberList = subscriberOperations.getAllActiveAndTrialSubscriptionsForUser(chatId);
        String text = "У Вас нет активных подписок";

        StringBuilder resultSb = new StringBuilder();

        if (subscriberList != null && !subscriberList.isEmpty()) {

            resultSubscriberList = new ArrayList<>(subscriberList);
            resultSb.insert(0, "Выберите подписку, которую хотите отменить\n\n");

            StringBuilder stringBuilder = new StringBuilder();
            subscriberList.forEach(subscriber -> {

                // Наименование спецтехники
                stringBuilder
                        .append("\n")
                        .append("*=>")
                        .append(connectionOperations.getNameByEquipmentType(subscriber.getEquipmentType()))
                        .append(":*\n");

                if (subscriber.getStatus().equals("Active")) {
                    stringBuilder.append("Тип: Платная").append("\n");
                    stringBuilder.append("Статус: Активная").append("\n");

                    // Получаем информацию о платеже по ID платежа
                    Payment payment = paymentOperations.getPaymentByPaymentId(subscriber.getPaymentId());

                    // Конвертируем дату следующего платежа в нужный формат
                    DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                    String nextBillDate = newFormatter.format(sourceFormatter.parse(payment.getNextBillDate()));
                    stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    stringBuilder.append("Стоимость: ").append(payment.getAmount().getValue()).append(" руб.").append("\n");
                } else if (subscriber.getStatus().equals("AutoPay")) {
                    stringBuilder.append("Тип: Платная").append("\n");
                    stringBuilder.append("Статус: Ожидается автоплатеж").append("\n");

                    // Получаем информацию о платеже по ID платежа
                    Payment payment = paymentOperations.getPaymentByPaymentId(subscriber.getPaymentId());

                    // Из таблицы ErrorPayments получаем дату ошибки (errorDate) и номер попытки (tryCount) по родительскому ID платежа (paymentMethodId)
                    ErrorPayment errorPayment = paymentOperations.getErrorInfoByPaymentMethodId(payment.getPaymentMethod().getId());
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime errorDateTime = LocalDateTime.parse(errorPayment.getErrorDate(), formatter);

                    // Если tryCount = 0 или 1 - значит к errorDate прибавляем 2 дня
                    if (errorPayment.getTryCount() == 0 || errorPayment.getTryCount() == 1) {
                        LocalDateTime plusTwoDaysDateTime = errorDateTime.plusDays(2);
                        formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        String nextBillDate = formatter.format(plusTwoDaysDateTime);
                        stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    }
                    // Если tryCount = 2 - значит к errorDate прибавляем 5 дней
                    if (errorPayment.getTryCount() == 2) {
                        LocalDateTime plusFiveDaysDateTime = errorDateTime.plusDays(5);
                        formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        String nextBillDate = formatter.format(plusFiveDaysDateTime);
                        stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    }

                    stringBuilder.append("Стоимость: ").append(payment.getAmount().getValue()).append(" руб.").append("\n");

                } else if (subscriber.getStatus().equals("Trial")) {
                    if (subscriber.getConnectCount() > 30) {
                        stringBuilder.append("Тип: Бесплатная").append("\n");
                    } else {
                        stringBuilder.append("Тип: Пробная").append("\n");
                        stringBuilder.append("Бесплатных просмотров: ").append(subscriber.getConnectCount()).append("\n");
                    }
                }
            });
            resultSb.append(stringBuilder);
        }

        // Дополнительно получаем подписки в статусе 'Active', НО с флажком isAutoPay = false
        // То есть автоплатеж отменили, но подписка еще действует
        subscriberList = subscriberOperations.getAllActiveWithoutAutoPaySubscriptionsForUser(chatId);

        if (subscriberList != null && !subscriberList.isEmpty()) {

            if (resultSubscriberList == null)
                resultSubscriberList = new ArrayList<>();

            resultSubscriberList.addAll(subscriberList);

            if (resultSb.toString().isEmpty()) {
                resultSb.insert(0, "Выберите подписку, которую хотите отменить\n\n");
            }

            StringBuilder stringBuilder = new StringBuilder();
            subscriberList.forEach(subscriber -> {

                // Наименование спецтехники
                stringBuilder
                        .append("\n")
                        .append("*=>")
                        .append(connectionOperations.getNameByEquipmentType(subscriber.getEquipmentType()))
                        .append(":*\n");
                stringBuilder.append("Тип: Платная").append("\n");
                stringBuilder.append("Статус: Активная").append("\n");

                // Получаем информацию о платеже по ID платежа
                Payment payment = paymentOperations.getPaymentByPaymentId(subscriber.getPaymentId());
                if (payment == null) {
                    logger.log(Level.SEVERE, "payment is null for paymentId - {0}", subscriber.getPaymentId());
                    return;
                }

                // Конвертируем дату следующего платежа в нужный формат
                DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                String nextBillDate = newFormatter.format(sourceFormatter.parse(payment.getNextBillDate()));
                stringBuilder.append("Действует до: ").append(nextBillDate).append("\n");
            });
            resultSb.append(stringBuilder);
        }

        // Формируем кнопки
        ReplyKeyboard replyKeyboard = (resultSubscriberList != null && !resultSubscriberList.isEmpty()) ? getAllActiveAndTrialSubscriptionButtons(resultSubscriberList) : null;

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(resultSb.toString().isEmpty() ?
                        text
                        :
                        resultSb.toString()
                                .replaceAll("\\=", "\\\\=")
                                .replaceAll("\\>", "\\\\>")
                                .replaceAll("\\.", "\\\\.")
                                .replaceAll("\\(", "\\\\(")
                                .replaceAll("\\)", "\\\\)")
                                .replaceAll("\\-", "\\\\-")
                )
                .parseMode(ParseMode.MARKDOWNV2)
                .replyMarkup(replyKeyboard)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getHelpInfo(String chatId) {
        try {
            String text = "Привет! Я бот Строитель! 🚜\n\n" +
                    "Здесь вы сможете получать заказы на спецтехнику по подписке. Бот работает по Москве и МО.\n" +
                    "Для размещения заказов в боте воспользуйтесь нашим Android приложением [Строитель](https://play.google.com/store/apps/details?id=com.builders)\n\n" +
                    "*Как работает бот:*\n" +
                    "1️⃣ Выберите тип спецтехники, на которую хотите получать заказы - кнопка *Выбрать спецтехнику*\n" +
                    "▶️ На каждый тип спецтехники подписка приобретается отдельно\n" +
                    "▶️ Среди спецтехник есть бесплатные. Для бесплатной спецтехники номер телефона заказчика можно просматривать неограниченное количество раз\n" +
                    "▶️ Если спецтехника платная, то сначала оформляется пробная подписка. В рамках пробного периода вам будет доступно определенное кол-во бесплатных просмотров номера телефона заказчика - кнопка *Показать телефон*\n" +
                    "2️⃣ После выбора спецтехники вам будут приходить заказы на выбранную спецтехнику по мере их появления в базе. Для просмотра номера телефона заказчика необходимо нажать на кнопку *Показать телефон*\n" +
                    "3️⃣ Когда истек пробный период на спецтехнику, бот при нажатии на кнопку *Показать телефон* предложит оформить платную подписку и укажет тарифы. Выбрав нужный тариф бот запросит email адрес.\n" +
                    "Обращаю ваше внимание, что email адрес является обязательным полем и его необходимо указывать корректно. На данный адрес после оплаты придет электронный чек. \n" +
                    "Если после ввода email адреса вы получаете ошибку, значит email адрес указан в неверном формате. Если email адрес был указан корректно, то бот сформирует счет на оплату подписки.\n" +
                    "По кнопке *Оплатить* вы сможете оплатить подписку.\n" +
                    "После оплаты бот пришлет оповещение, что оплата прошла успешно и вы будете получать заказы сразу с открытым номером телефона.\n" +
                    "▶️ Бот работает по подписке. То есть после истечения срока подписки будет выполнен автоплатеж. За 24 часа до автосписания бот пришлет оповещение. Вы всегда можете отписаться от платной подписки нажав на кнопку *Управление подписками*\n" +
                    "▶️ Автоплатежи. При неудачном автоплатеже будет выполнено еще несколько попыток автосписания средств: через 2 и через 5 дней от даты неудачного автоплатежа.\n" +
                    "Вы можете пополнить баланс карты и дождаться следующего автоматического списания средств, при этом нет необходимости завершать текущую подписку и оформлять ее заново.\n" +
                    "В течение 5 дней после неудачного автоплатежа у вас будет бесплатный доступ к заказам.\n" +
                    "4️⃣ Для связи с поддержкой нажмите на кнопку *Написать нам*\n" +
                    "5️⃣ Чтобы узнать стоимость подписки нажмите на кнопку *Узнать стоимость подписки* под сообщением\n\n" +
                    "*Кнопки:*\n" +
                    "⏺️*Выбрать спецтехнику* - Выбор спецтехники, на которую необходимо получать заказы\n" +
                    "⏺️*Управление подписками* - Информация по текущим подпискам: Тип подписки, кол-во бесплатных просмотров, дата следующего платежа и тд\n" +
                    "⏺️*Написать нам* - Обращение в поддержку, если у вас возникли вопросы \n" +
                    "⏺️*Помощь* - Информация по работе бота и стоимости подписки";

            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text
                            .replaceAll("\\!", "\\\\!")
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("\\-", "\\\\-")
                            .replaceAll("\\=", "\\\\=")
                    )
                    .parseMode(ParseMode.MARKDOWNV2)
                    .disableWebPagePreview(true)
                    .replyMarkup(getPriceButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getPriceInfo(String chatId) {
        // Получаем из БД Firebase все виды спецтехники
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();
        // Сортируем по алфавиту наименования спецтехники
        heavyTypes.sort(Comparator.comparing(object -> object.getFields().getName().getStringValue()));

        StringBuilder stringBuilder = new StringBuilder();

        // Проходим по списку спецтехник
        heavyTypes.forEach(heavy -> {
            // По каждой спецтехнике получаем тарифы
            List<Subscription> subscriptions = subscriptionOperations.getAllSubscriptions(String.valueOf(heavy.getFields().getId().getStringValue()));

            // Для бесплатной спецтехники ставим только отметку "Бесплатно", без указания тарифов
            if (subscriptions.get(0).getAmount().equals("0.00")) {
                stringBuilder.append("<b>=>").append(heavy.getFields().getName().getStringValue()).append(":</b> Бесплатно\n\n");
                return;
            }

            stringBuilder.append("<b>=>").append(heavy.getFields().getName().getStringValue()).append(":</b>\n");
            subscriptions.forEach(subscription -> {
                // Игнорируем тестовый тариф
                if (subscription.getSubscriptionType() == SubscriptionType.test)
                    return;
                // Указываем тариф и стоимость
                stringBuilder.append(subscription.getDescription()).append(" - ").append(subscription.getAmount()).append(" руб.\n");
            });
            stringBuilder.append("\n");
        });

        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(stringBuilder.toString())
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(getMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void unsubscribe(String chatId, String equipmentType, Integer messageId) {
        List<Subscriber> subscriberList = subscriberOperations.getAllActiveAndTrialSubscriptionsForUser(chatId);
        Subscriber subscriber = subscriberList.stream().filter(subscr -> subscr.getEquipmentType().equals(equipmentType)).findAny().orElse(null);

        // При выборе конкретной подписки необходимо проверить, является ли она оплаченой (статус Active)
        //  - Если статус Trial, то проставляем в БД статус Expired, expiredDate как текущую дату + удаляем из Redis пользователя для данной спецтехники
        //  - Если статус Active, значит подписка была оплачена. Проставляем в БД флажок isAutoPay = false, чтобы следующий автоплатеж не прошел

        if (subscriber == null) {
            logger.log(Level.SEVERE, "Subscriber object for id - {0} and equipmentType - {1} from unsubscribe method is null!", new Object[]{chatId, equipmentType});
            return;
        }

        logger.log(Level.SEVERE, "Method: unsubscribe >> Subscriber info for id - {0} and equipmentType - {1}: {2}", new Object[]{chatId, equipmentType, new Gson().toJson(subscriber)});

        boolean isTrialSubscr = subscriber.getStatus().equals("Trial");

        if (isTrialSubscr) {
            logger.log(Level.SEVERE, "CANCEL TRIAL SUBSCRIPTION: userId - {0}, equipmentType - {1}", new Object[]{chatId, equipmentType});
            subscriberOperations.cancelTrialSubscription(chatId, equipmentType, false);
            // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы, пока не подпишется снова
            redisEntity.remElement(subscriber.getEquipmentType(), chatId);
        } else {
            logger.log(Level.SEVERE, "CANCEL PAID SUBSCRIPTION: userId - {0}, equipmentType - {1}", new Object[]{chatId, equipmentType});
            subscriberOperations.cancelPaidSubscription(chatId, equipmentType);
        }

        String text = isTrialSubscr ? "❗Пробная подписка на получение заказов по спецтехнике <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b> успешно отменена!\nВы в любой момент можете возобновить подписку."
                : "❗Платная подписка на получение заказов по спецтехнике <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b> успешно отменена!\nВы будете продолжать получать заказы по данной спецтехнике с номером телефона в открытом виде до окончания срока текущей подписки.\nНовую подписку Вы сможете оформить после окончания текущей.";

        // Если подписка была отменена после неудачного автоплатежа
        if (subscriber.getStatus().equals("AutoPay")) {
            logger.log(Level.SEVERE, "CANCEL SUBSCRIPTION: status - AutoPay, userId - {0}, equipmentType - {1}", new Object[]{chatId, equipmentType});
            text = "❗Подписка на получение заказов по спецтехнике <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b> успешно отменена!\n" +
                    "Вы в любой момент можете возобновить подписку.\n" +
                    "Для этого Вам необходимо заново подписаться на данную спецтехнику";
            // Закрываем подписку текущей датой
            subscriberOperations.updateSubscriptionInfo(subscriber.getUserId(), subscriber.getPaymentId(), dateFormat.format(Calendar.getInstance().getTime()), true);
            // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы, пока не подпишется снова
            redisEntity.remElement(subscriber.getEquipmentType(), subscriber.getUserId());
        }

        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode(ParseMode.HTML)
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }

        // Получаем все актвные/платные и триальные подписки  (+ подписки в статусе 'AutoPay')
        List<Subscriber> resultSubscriberList = null;
        subscriberList = subscriberOperations.getAllActiveAndTrialSubscriptionsForUser(chatId);

        StringBuilder resultSb = new StringBuilder();

        if (subscriberList != null && !subscriberList.isEmpty()) {

            resultSubscriberList = new ArrayList<>(subscriberList);
            resultSb.insert(0, "Выберите подписку, которую хотите отменить\n\n");

            StringBuilder stringBuilder = new StringBuilder();
            subscriberList.forEach(subscr -> {

                // Наименование спецтехники
                stringBuilder
                        .append("\n")
                        .append("*=>")
                        .append(connectionOperations.getNameByEquipmentType(subscr.getEquipmentType()))
                        .append(":*\n");

                if (subscr.getStatus().equals("Active")) {
                    stringBuilder.append("Тип: Платная").append("\n");
                    stringBuilder.append("Статус: Активная").append("\n");

                    // Получаем информацию о платеже по ID платежа
                    Payment payment = paymentOperations.getPaymentByPaymentId(subscr.getPaymentId());

                    // Конвертируем дату следующего платежа в нужный формат
                    DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                    String nextBillDate = newFormatter.format(sourceFormatter.parse(payment.getNextBillDate()));
                    stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    stringBuilder.append("Стоимость: ").append(payment.getAmount().getValue()).append(" руб.").append("\n");
                } else if (subscr.getStatus().equals("AutoPay")) {
                    stringBuilder.append("Тип: Платная").append("\n");
                    stringBuilder.append("Статус: Ожидается автоплатеж").append("\n");

                    // Получаем информацию о платеже по ID платежа
                    Payment payment = paymentOperations.getPaymentByPaymentId(subscr.getPaymentId());

                    // Из таблицы ErrorPayments получаем дату ошибки (errorDate) и номер попытки (tryCount) по родительскому ID платежа (paymentMethodId)
                    ErrorPayment errorPayment = paymentOperations.getErrorInfoByPaymentMethodId(payment.getPaymentMethod().getId());
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime errorDateTime = LocalDateTime.parse(errorPayment.getErrorDate(), formatter);

                    // Если tryCount = 0 или 1 - значит к errorDate прибавляем 2 дня
                    if (errorPayment.getTryCount() == 0 || errorPayment.getTryCount() == 1) {
                        LocalDateTime plusTwoDaysDateTime = errorDateTime.plusDays(2);
                        formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        String nextBillDate = formatter.format(plusTwoDaysDateTime);
                        stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    }
                    // Если tryCount = 2 - значит к errorDate прибавляем 5 дней
                    if (errorPayment.getTryCount() == 2) {
                        LocalDateTime plusFiveDaysDateTime = errorDateTime.plusDays(5);
                        formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        String nextBillDate = formatter.format(plusFiveDaysDateTime);
                        stringBuilder.append("Следующий платеж: ").append(nextBillDate).append("\n");
                    }

                    stringBuilder.append("Стоимость: ").append(payment.getAmount().getValue()).append(" руб.").append("\n");

                } else if (subscr.getStatus().equals("Trial")) {
                    if (subscr.getConnectCount() > 30) {
                        stringBuilder.append("Тип: Бесплатная").append("\n");
                    } else {
                        stringBuilder.append("Тип: Пробная").append("\n");
                        stringBuilder.append("Бесплатных просмотров: ").append(subscr.getConnectCount()).append("\n");
                    }
                }
            });
            resultSb.append(stringBuilder);
        }

        // Дополнительно получаем подписки в статусе 'Active', НО с флажком isAutoPay = false
        // То есть автоплатеж отменили, но подписка еще действует
        subscriberList = subscriberOperations.getAllActiveWithoutAutoPaySubscriptionsForUser(chatId);

        if (subscriberList != null && !subscriberList.isEmpty()) {

            if (resultSubscriberList == null)
                resultSubscriberList = new ArrayList<>();

            resultSubscriberList.addAll(subscriberList);

            if (resultSb.toString().isEmpty()) {
                resultSb.insert(0, "Выберите подписку, которую хотите отменить\n\n");
            }

            StringBuilder stringBuilder = new StringBuilder();
            subscriberList.forEach(subs -> {

                // Наименование спецтехники
                stringBuilder
                        .append("\n")
                        .append("*=>")
                        .append(connectionOperations.getNameByEquipmentType(subs.getEquipmentType()))
                        .append(":*\n");
                stringBuilder.append("Тип: Платная").append("\n");
                stringBuilder.append("Статус: Активная").append("\n");

                // Получаем информацию о платеже по ID платежа
                Payment payment = paymentOperations.getPaymentByPaymentId(subs.getPaymentId());
                if (payment == null) {
                    logger.log(Level.SEVERE, "payment is null for paymentId - {0}", subs.getPaymentId());
                    return;
                }
                // Конвертируем дату следующего платежа в нужный формат
                DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                String nextBillDate = newFormatter.format(sourceFormatter.parse(payment.getNextBillDate()));
                stringBuilder.append("Действует до: ").append(nextBillDate).append("\n");
            });
            resultSb.append(stringBuilder);
        }

        // Формируем кнопки
        ReplyKeyboard replyKeyboard = (resultSubscriberList != null && !resultSubscriberList.isEmpty()) ? getAllActiveAndTrialSubscriptionButtons(resultSubscriberList) : null;

        // Если подписок уже нет, то удаляем сообщение с выбором подписки
        // При этом НЕ удаляем сообщение, если осталась АКТИВНАЯ подписка, которую отменили ДО автоплатежа
        // То есть если есть хотя бы одна подписка в статусе 'Active' и с флажком isAutoPay = false
        if (
                (resultSubscriberList == null || resultSubscriberList.isEmpty())
                        && ((subscriberList != null && !subscriberList.isEmpty() && subscriberList.stream().noneMatch(subscr -> subscr.getStatus().equals("Active") && !subscr.isAutoPay()))
                        || resultSb.toString().isEmpty())
        ) {
            try {
                new BuilderBot().execute(DeleteMessage.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
        } else {
            // Если подписки еще есть, то редактируем сообщение со списком подписок: обновляем кнопки с подписками
            try {
                new BuilderBot().execute(EditMessageText.builder()
                        .chatId(chatId)
                        .text(resultSb.toString()
                                .replaceAll("\\=", "\\\\=")
                                .replaceAll("\\>", "\\\\>")
                                .replaceAll("\\.", "\\\\.")
                                .replaceAll("\\(", "\\\\(")
                                .replaceAll("\\)", "\\\\)")
                                .replaceAll("\\-", "\\\\-"))
                        .parseMode(ParseMode.MARKDOWNV2)
                        .messageId(messageId)
                        .replyMarkup((InlineKeyboardMarkup) replyKeyboard)
                        .build());
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }

        // Если была отменена платная подписка - дополнительно запрашиваем фидбек у пользователя
        if (!isTrialSubscr) {
            this.sendRequestFeedbackAboutUnsubscribe(chatId);
        }
    }

    @Override
    public void requestProblem(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Какой у вас вопрос?")
                .replyMarkup(getCloseDialogButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
        redisEntity.setElement(chatId.concat("_help"), "true");
    }

    @Override
    public boolean checkSubscriberIsAlreadyExist(String userId) {
        return subscriberOperations.checkSubscriberIsAlreadyExist(userId);
    }

    @Override
    public void sayUnexpectedError(String userId, String message) {
        String text = message.concat(" Пожалуйста, сообщите об этом в поддержку. Нажмите на кнопку 'Главное меню'👇");
        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(userId)
                    .text(text)
                    .replyMarkup(getMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sayAutoPaymentError(String userId, String message) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(message)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendMessageAboutSuccessfulPayment(String userId, String equipmentType) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text("❗️Поздравляю, оплата прошла успешно!\n" +
                        "Теперь Вы будете получать новые заказы по спецтехнике <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b>, в которых номер телефона будет указан сразу.\n\n" +
                        "Вы можете отписаться от платной подписки в любой момент, нажав на кнопку ГЛАВНОЕ МЕНЮ, затем УПРАВЛЕНИЕ ПОДПИСКАМИ.")
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendMessageAboutCanceledPayment(String userId, String reasonValue) {
        logger.log(Level.INFO, "method - Send message about payment is canceled, userId - {0}", userId);
        // Достаем описание отмены платежа из БД по коду причины
        String description = dictionaryOperations.getErrorDescriptionByReason(reasonValue);
        String message = "❗️К сожалению, Ваш платеж не прошел!\n\n<b>Причина:</b> " + description + "\n\n<b>Для оплаты подписки Вам необходимо сформировать новый счет и оплатить его.</b>";
        SendMessage sendMessage = SendMessage.builder()
                .chatId(userId)
                .text(message)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendWarningMessageAboutSubscriptionWillBeExpired(String chatId, String paymentId, Calendar warningTime) {
        long delay = warningTime.getTime().getTime() - Calendar.getInstance().getTimeInMillis();

        Subscriber subscriber = subscriberOperations.getSubscriberByPaymentId(paymentId);
        String message = "❗️Привет! На связи бот строитель 🚜\n" +
                "Обращаю Ваше внимание, что *подписка на спецтехнику " + firebase.getHeavyMachineryNameById(subscriber.getEquipmentType()).toUpperCase() + " истекает через 24 часа*. Далее произойдет *автоматическое списание средств*.\n" +
                "Если Вы хотите *отменить* подписку - *нажмите на кнопку ГЛАВНОЕ МЕНЮ, затем УПРАВЛЕНИЕ ПОДПИСКАМИ*.\n\n" +
                "*Если Вы уже отменили подписку, то никаких действий не требуется! Я оповещу Вас об окончании подписки.*";

        // Если delay отрицальный, то сообщаем пользователю, что до окончания подписки менее 24 часов
        if (delay < 0) {
            message = "❗️Привет! На связи бот строитель 🚜\n" +
                    "Обращаю Ваше внимание, что до *окончания подписки на спецтехнику " + firebase.getHeavyMachineryNameById(subscriber.getEquipmentType()).toUpperCase() + " МЕНЕЕ 24 часов!* Далее произойдет *автоматическое списание средств*.\n" +
                    "Если Вы хотите *отменить* подписку - *нажмите на кнопку ГЛАВНОЕ МЕНЮ, затем УПРАВЛЕНИЕ ПОДПИСКАМИ*.\n\n" +
                    "*Если Вы уже отменили подписку, то никаких действий не требуется! Я оповещу Вас об окончании подписки.*";
        }

        logger.log(Level.INFO, "Create task to warning user - {0} about subscription with delay - {1}", new Object[]{chatId, delay});
        String finalMessage = message;
        executorService.schedule(() -> {
            logger.log(Level.INFO, "Send warning message about subscription will be expired for userId - {0}", chatId);
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(finalMessage
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("\\,", "\\\\,")
                            .replaceAll("\\!", "\\\\!")
                            .replaceAll("\\-", "\\\\-")
                            .replaceAll("\\)", "\\\\)")
                            .replaceAll("\\(", "\\\\("))
                    .parseMode(ParseMode.MARKDOWNV2)
                    .replyMarkup(getMainMenuButton())
                    .build();
            try {
                new BuilderBot().execute(sendMessage);
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendMessageAboutCanceledAutoPayment(String chatId, String reasonValue) {
        logger.log(Level.INFO, "method - Send message about AUTO payment is canceled, userId - {0}", chatId);
        // Достаем описание отмены платежа из БД по коду причины
        String description = dictionaryOperations.getErrorDescriptionByReason(reasonValue);
        String message = "❗️К сожалению, автоплатеж по Вашей карте не прошел!\n\n" +
                "<b>Причина:</b> " + description + "\n\n" +
                "<b>Я предприму еще несколько попыток автосписания средств: через 2 дня и через 5 дней.</b>\n\n" +
                "ℹ️Вы можете отменить текущую подписку, чтобы не было повторного списания средств.\n" +
                "Для этого нажмите на кнопку <b>УПРАВЛЕНИЕ ПОДПИСКАМИ</b> и выберите соответствующую спецтехнику";
        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(message)
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(getMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendMessageAboutSuccessfullyAutoPayment(String chatId, String equipmentType) {
        logger.log(Level.INFO, "method - send message about successfully auto payment for userId - {0}", chatId);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("❗Автоплатеж прошел успешно. Ваша подписка на спецтехнику <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b> продлена.")
                .replyMarkup(getMainMenuButton())
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sayHelloAfterUnsubscribe(String chatId, String equipmentType) {
        String helloMessage = "❗️Привет! На связи бот строитель 🚜\n" +
                "Ваша подписка на спецтхенику <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b> закончилась! \n\n" +
                "<b>Чтобы заново подписаться на необходимую спецтехнику - нажмите на кнопку Выбрать спецтехнику</b>👇🏻";
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(helloMessage)
                .replyMarkup(getMainMenuButton())
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendMessageAboutAutoPaymentError(String chatId, String equipmentType) {
        logger.log(Level.INFO, "method - Send message about AUTO payment error, userId - {0}", chatId);

        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setRemoveKeyboard(true);
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("❗️️Привет! На связи бот строитель 🚜\n" +
                        "Мне не удалось провести автоплатеж для подписки на спецтехнику <b>" + firebase.getHeavyMachineryNameById(equipmentType) + "</b>!😔\n" +
                        "Я предприму еще несколько попыток и сообщу результат. Это может занять от 15 до 20 минут.\n\n" +
                        "<b>Во избежание повторного списания средств, просьба не оформлять новую подписку!</b>")
                .replyMarkup(replyKeyboardRemove)
                .parseMode(ParseMode.HTML)
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void feedbackRequest(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("🚜 Введите свое сообщение")
                .replyMarkup(getCancelFeedbackButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
        this.redisEntity.setElement(chatId.concat("_feedback"), "true");
    }

    @Override
    public void cancelFeedback(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Ввод сообщения отменен 🙅🏻‍♂️")
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
        this.redisEntity.deleteElement(chatId.concat("_feedback"));
    }

    @Override
    public void sendThanksToUserForHisFeedback(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("🚜 Благодарю за обратную связь!")
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
        this.redisEntity.deleteElement(chatId.concat("_feedback"));
    }

    @Override
    public void deleteInactiveUsersByAdmin(String userId) {
        logger.log(Level.SEVERE, "Start delete inactive subscriptions by admin - {0} ", userId);
        // Получить все подписки в статусе 'Trial', без email и у которых НЕ указана дата.
        // Также не должна быть указана дата завершения подписки
        // Это будут подписки, которые были оформлены ДО 17 июня
        List<Subscriber> inactiveSubscriptions = this.subscriberOperations.getOldInactiveUsers();

        if (inactiveSubscriptions == null || inactiveSubscriptions.isEmpty()) {
            logger.log(Level.SEVERE, "inactiveSubscriptions is null or is empty, return ...");
            try {
                new BuilderBot().execute(
                        SendMessage.builder()
                                .chatId(userId)
                                .text("❗️Неактивных подписчиков не найдено!")
                                .build());
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
            return;
        }

        logger.log(Level.SEVERE, "inactiveSubscriptions size = {0}", inactiveSubscriptions.size());

        // Пробегаемся по каждой подписке и закрываем ее текущей датой
        // Также удаляем подписчика из списка рассылки в Redis для соответствующей спецтехники
        inactiveSubscriptions.forEach(inactiveSubscriber -> {
            logger.log(Level.SEVERE, "Delete old inactive subscription - {0} ", new Gson().toJson(inactiveSubscriber));
            subscriberOperations.cancelInactiveOLDTrialSubscription(inactiveSubscriber.getUserId(), inactiveSubscriber.getEquipmentType());
            // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы
            redisEntity.remElement(inactiveSubscriber.getEquipmentType(), inactiveSubscriber.getUserId());
        });

        logger.log(Level.SEVERE, "Successfully removed {0} subscriptions ...", inactiveSubscriptions.size());

        try {
            new BuilderBot().execute(
                    SendMessage.builder()
                            .chatId(userId)
                            .text("Успешно удалено " + inactiveSubscriptions.size() + " неактивных подписок ✅")
                            .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void deleteInactiveUsersBySchedule() {
        // Получаем дату, раньше которой будут удалены все ПРОБНЫЕ подписки
        // (Текущая дата - 1 месяц)
        LocalDateTime beforeLocalDate = LocalDateTime
                .now()
                .minusMonths(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String beforeDate = beforeLocalDate.format(formatter);
        logger.log(Level.SEVERE, "Delete before date - {0}", beforeDate);

        // Получить все подписки по следующим критериям:
        // - Статус Trial (пробная)
        // - Не указан email
        // - Не указана дата окончания подписки
        // - Дата подписки РАНЬШЕ, чем "текущая дата минус 4 месяца".
        List<Subscriber> inactiveUsersForMoreThanFourMonths = this.subscriberOperations.getInactiveUsersForMoreThanFourMonths(beforeDate);

        // Если таких подписок нет, то завершаем работу метода
        if (inactiveUsersForMoreThanFourMonths == null || inactiveUsersForMoreThanFourMonths.isEmpty()) {
            logger.log(Level.SEVERE, "inactiveUsersForMoreThanFourMonths is null or is empty, return ...");
            return;
        }

        logger.log(Level.SEVERE, "inactiveUsersForMoreThanFourMonths size = {0}", inactiveUsersForMoreThanFourMonths.size());

        // Пробегаемся по каждой подписке и закрываем ее текущей датой
        // Также удаляем подписчика из списка рассылки в Redis для соответствующей спецтехники
        inactiveUsersForMoreThanFourMonths.forEach(inactiveSubscriber -> {
            logger.log(Level.SEVERE, "Delete inactive subscription - {0} ", new Gson().toJson(inactiveSubscriber));
            subscriberOperations.cancelInactiveTrialSubscription(inactiveSubscriber.getUserId(), inactiveSubscriber.getEquipmentType(), beforeDate);
            // В Redis удаляем пользователя для текущего типа специехники, чтобы он не получал новые заказы
            redisEntity.remElement(inactiveSubscriber.getEquipmentType(), inactiveSubscriber.getUserId());
        });

        logger.log(Level.SEVERE, "Successfully removed {0} subscriptions ...", inactiveUsersForMoreThanFourMonths.size());
    }

    @Override
    public void sendRequestFeedbackAboutUnsubscribe(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("🚜 Напишите, пожалуйста, почему Вы решили отменить подписку. Это позволит мне стать лучше! 🙏")
                .replyMarkup(getFeedbackButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private InlineKeyboardMarkup getFeedbackButton() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .text("Написать ✍️")
                .callbackData("write_feedback")
                .build();
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getPriceButton() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = InlineKeyboardButton.builder()
                .text("Узнать стоимость подписки 💵")
                .callbackData("price")
                .build();
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private ReplyKeyboardMarkup getCancelFeedbackButton() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRowList = new ArrayList<>();
        KeyboardButton keyboardButton = KeyboardButton.builder()
                .text("Отмена ❌")
                .build();
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add(keyboardButton);
        keyboardRowList.add(keyboardRow);
        replyKeyboardMarkup.setKeyboard(keyboardRowList);
        replyKeyboardMarkup.setResizeKeyboard(true);

        return replyKeyboardMarkup;
    }

    private InlineKeyboardMarkup getShowMainMenuButton() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Главное меню 🎯");
        inlineKeyboardButton.setCallbackData("main_menu");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);
        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getShowPhoneButton(boolean isActiveSubscr) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();

        if (isActiveSubscr) {
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText("Главное меню 🎯");
            inlineKeyboardButton.setCallbackData("main_menu");
            inlineKeyboardButtons.add(inlineKeyboardButton);
            listOfListInlineKey.add(inlineKeyboardButtons);
            inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);
            return inlineKeyboardMarkup;
        }

        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Показать телефон 📞");
        inlineKeyboardButton.setCallbackData("show_phone");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Главное меню 🎯");
        inlineKeyboardButton.setCallbackData("main_menu");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getHeavyMachineryTypeButtons() {

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();

        // Получаем из БД Firebase все виды спецтехники
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();
        // Сортируем по алфавиту наименования спецтехники
        heavyTypes.sort(Comparator.comparing(object -> object.getFields().getName().getStringValue()));

        int count = 0;
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
        for (Document heavyType : heavyTypes) {
            // Если index элемента четный, то сохраняем предыдущую пару кнопок и создаем новый блок из двух кнопок
            if (count % 2 == 0) {
                listOfListInlineKey.add(inlineKeyboardButtons);
                inlineKeyboardButtons = new ArrayList<>();
            }

            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText(heavyType.getFields().getName().getStringValue());
            inlineKeyboardButton.setCallbackData(heavyType.getFields().getId().getStringValue());
            inlineKeyboardButtons.add(inlineKeyboardButton);

            // Если элемент является последним, то сохраняем последнюю пару кнопок
            if (count == heavyTypes.size() - 1) {
                listOfListInlineKey.add(inlineKeyboardButtons);
            }

            // Сохраняем в Redis ID типов спецтехники, чтобы обрабатывать callback.
            // Когда пользователь по кнопке выбирает тип спецтехники, чтобы подписаться на нее.
            redisEntity.pushElement("heavyMachineryIds", heavyType.getFields().getId().getStringValue());
            count++;
        }
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);
        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getAllActiveAndTrialSubscriptionButtons(List<Subscriber> subscriberList) {

        if (subscriberList == null)
            return null;

        // Если платную подписку отменили ДО ее завершения, то НЕ отображаем кнопку с этой спецтехникой
        subscriberList.removeIf(subscriber ->
                subscriber.getStatus().equals("Active")
                        && !subscriber.isAutoPay());

        // Получаем из БД Firebase все виды спецтехники
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();

        // Проходим по подпискам и задаем имя спецтехники для каждой подписки
        for (Document heavyType : heavyTypes) {
            for (Subscriber subscr : subscriberList) {
                if (subscr.getEquipmentType().equals(heavyType.getFields().getId().getStringValue())) {
                    subscr.setEquipmentName(heavyType.getFields().getName().getStringValue());
                }
            }
        }

        // Сортируем по алфавиту наименования спецтехники
        subscriberList.sort(Comparator.comparing(Subscriber::getEquipmentName));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();

        int count = 0;
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
        for (Subscriber subscriber : subscriberList) {
            // Если index элемента четный, то сохраняем предыдущую пару кнопок и создаем новый блок из двух кнопок
            if (count % 2 == 0) {
                listOfListInlineKey.add(inlineKeyboardButtons);
                inlineKeyboardButtons = new ArrayList<>();
            }

            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText(subscriber.getEquipmentName());
            inlineKeyboardButton.setCallbackData(subscriber.getEquipmentType().concat("_unsubscribe"));
            inlineKeyboardButtons.add(inlineKeyboardButton);

            // Если элемент является последним, то сохраняем последнюю пару кнопок
            if (count == subscriberList.size() - 1) {
                listOfListInlineKey.add(inlineKeyboardButtons);
            }
            count++;
        }
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);
        return inlineKeyboardMarkup;
    }

    private ReplyKeyboardMarkup getCloseDialogButton() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(
                        Collections.singletonList(new KeyboardButton("Закрыть диалог ❌"))))
                .resizeKeyboard(true)
                .build();
    }
}
