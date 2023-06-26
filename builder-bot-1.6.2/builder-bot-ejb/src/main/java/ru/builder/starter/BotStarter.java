package ru.builder.starter;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.builder.bean.admin.Admin;
import ru.builder.bean.general.General;
import ru.builder.bean.payment.Payment;
import ru.builder.bean.statistic.Statistic;
import ru.builder.bot.BuilderBot;
import ru.builder.db.connection.ConnectionOperations;
import ru.builder.db.dictionary.DictionaryOperations;
import ru.builder.db.document.DocumentOperations;
import ru.builder.db.order.OrderInfoOperations;
import ru.builder.db.payment.PaymentOperations;
import ru.builder.db.subscriber.SubscriberOperations;
import ru.builder.db.subscription.SubscriptionOperations;
import ru.builder.model.payment.CreatePayment;
import ru.builder.model.subscr.Subscriber;
import ru.builder.redis.RedisEntity;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.inject.Inject;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
@AccessTimeout(15000)
public class BotStarter {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private BotSession botSession;

    @Inject
    private General general;
    @Inject
    private DocumentOperations documentOperations;
    @Inject
    private SubscriptionOperations subscriptionOperations;
    @Inject
    private PaymentOperations paymentOperations;
    @Inject
    private SubscriberOperations subscriberOperations;
    @Inject
    private DictionaryOperations dictionaryOperations;
    @Inject
    private Payment payment;
    @Inject
    private OrderInfoOperations orderInfoOperations;
    @Inject
    private Admin admin;
    @Inject
    private Statistic statistic;
    @Inject
    private ConnectionOperations connectionOperations;
    @Inject
    private RedisEntity redisEntity;

    @PostConstruct
    public void init() {
        try {
            logger.log(Level.SEVERE, "ApiContextInitializer...");
            logger.log(Level.SEVERE, "Initialization BotsApi....");
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);

            logger.log(Level.SEVERE, "OK!");
            logger.log(Level.SEVERE, "Register BuilderBot ...");
            botSession = telegramBotsApi.registerBot(new BuilderBot(general, payment, admin, statistic, redisEntity));
            logger.log(Level.SEVERE, "Register done!");
            logger.log(Level.SEVERE, "BuilderBot was started...");

            // Создаем таблицы
            subscriptionOperations.createTable();
            paymentOperations.createTable();
            paymentOperations.createErrorPaymentsTable();
            subscriberOperations.createTable();
            dictionaryOperations.createTable();
            connectionOperations.createTable();

            // ===> Запускаем задачи на повтор ошибочных автоплатежей после рестарта контейнера
            // Для ошибочных платежей в статусе 'wait' проставляем статус 'canceled', чтобы взять их в обработку повторно
            List<CreatePayment> waitPayments = paymentOperations.getErrorPaymentsByStatus("wait");
            if (waitPayments != null
                    && !waitPayments.isEmpty()) {
                waitPayments.forEach(payment -> {
                    // Если значение tryCount = 1, значит повторный автоплатеж не успел выполнить 1-ую попытку
                    // Соответственно устанавливаем этому платежу значение 0, чтобы он был выполнен повторно
                    // Если же значение tryCount будет 0 или 2, то достаточно просто поменять статус на 'canceled',
                    // потому что обрабатываются платежи со значениями tryCount = 0 и 2
                    if (payment.getTryCount() == 1) {
                        this.paymentOperations.setRetryCountForErrorAutoPayment(payment.getPaymentMethodId(), -1);
                    }
                    this.paymentOperations.setNewStatusForErrorAutoPayment(payment.getPaymentMethodId(), "canceled", "wait");
                });
            }

            // Запускаем задачи на автоплатеж после рестарта контейнера
            List<Subscriber> subscriberList = subscriberOperations.getAllActiveSubscribers();
            final ExecutorService executorService = Executors.newFixedThreadPool(5);
            subscriberList.forEach(subscriber -> {
                ru.builder.model.payment.Payment autoPayment = paymentOperations.getPaymentByPaymentId(subscriber.getPaymentId());
                if (autoPayment == null) {
                    logger.log(Level.SEVERE, "autoPayment is null for paymentId - {0}", subscriber.getPaymentId());
                    return;
                }
                try {
                    Date date = dateFormat.parse(autoPayment.getNextBillDate());
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    logger.log(Level.SEVERE, "RESTART BOT: Start task for schedule autoPayment ...");
                    executorService.submit(() -> payment.createAutoPayment(autoPayment, cal));
                } catch (ParseException e) {
                    logger.log(Level.SEVERE, null, e);
                }
            });
            executorService.shutdown();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception: ", ex);
        }
    }

    @Schedule(hour = "*", minute = "*", second = "*/10")
    @Lock(LockType.READ)
    public void getNewOrders() {
        general.getNewOrders();
    }

    @Schedule(hour = "*", minute = "*", second = "*/35")
    @Lock(LockType.READ)
    public void putOrdersToQueue() {
        this.general.putOrdersToQueue();
    }

    @Schedule(hour = "*", minute = "*/1")
    @Lock(LockType.READ)
    public void checkPaymentsStatus() {
        payment.checkPaymentsStatus();
    }

    @Schedule(hour = "*", minute = "*/5")
    @Lock(LockType.READ)
    public void retryErrorPayments() {
        payment.retryErrorPayments();
    }

    @Schedule(hour = "*", minute = "*/5")
    @Lock(LockType.READ)
    public void retryCanceledPayments() {
        payment.retryCanceledPayments();
    }

    @Schedule(hour = "1,10,15,22")
    @Lock(LockType.READ)
    public void deleteOlderOrders() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat.toPattern());

        // Берем текущее время, вычитаем из него 5 часов и получаем новую дату и время.
        // Из БД будут удалены все заказы, время которых ДО полученной даты
        LocalDateTime dtMinus5Hours = LocalDateTime.now().minusHours(5);
        logger.log(Level.INFO, "dtMinus5Hours - {0}", dtMinus5Hours);

        // ==> Делаем перестраховку от повторок на всякий случай:
        // Запоминаем дату последнего заказа в БД бота (дата, раньше которой мы удаляем заказы)
        long nowMinus5Hours = Timestamp.valueOf(dtMinus5Hours).getTime();
        redisEntity.setElement("lastOrderTime", String.valueOf(nowMinus5Hours));

        // Приводим дату к строке
        String strDtMinus5Hours = formatter.format(dtMinus5Hours);
        logger.log(Level.SEVERE, "Finding older Orders with date <= - {0}", strDtMinus5Hours);
        // Получаем все заказы, которые ДО (текущая дата - 5 часов)
        String documentIds = orderInfoOperations.getAllOrdersByDate(strDtMinus5Hours);
        if (documentIds == null || documentIds.isEmpty()) {
            logger.log(Level.SEVERE, "Not found older Orders, return...");
            return;
        }
        String[] idsArray = documentIds.replaceAll("\\(", "").replaceAll("\\)", "").split(",");
        logger.log(Level.SEVERE, "Found - {0} documentIds. Start deleting older Orders...", Arrays.asList(idsArray).size());
        logger.log(Level.INFO, "Found documentIds - {0}", documentIds);

        // Удаляем найденные заказы из БД
        orderInfoOperations.deleteOlderOrders(documentIds);
        // Также удаляем старые документы (заказы) из БД
        documentOperations.deleteOlderDocuments(documentIds);
        logger.log(Level.SEVERE, "{0} older Orders and Documents was deleted... ", Arrays.asList(idsArray).size());
    }


    @Schedule(hour = "00", minute = "00", second = "00")
    public void clearNewUsers() {
        logger.log(Level.SEVERE, "Remove new users from Redis ...");
        redisEntity.deleteElement("new_users");
        redisEntity.deleteElement("new_users_yandex_today");
    }

    @Schedule(dayOfMonth = "27")
    public void deleteInactiveUsers() {
        logger.log(Level.SEVERE, "Delete inactive users more than 1 month old ...");
        this.general.deleteInactiveUsersBySchedule();
    }

    @PreDestroy
    public void cleanup() {
        try {
            logger.log(Level.SEVERE, "Stop botSession...");
            if (botSession == null) {
                logger.log(Level.SEVERE, "botSession is null... return...");
                return;
            }
            botSession.stop();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Destroyed exception: ", ex);
        }
    }
}