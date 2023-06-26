package ru.builder.bean.statistic;

import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.builder.bean.firebase.Firebase;
import ru.builder.bot.BuilderBot;
import ru.builder.db.subscriber.SubscriberOperations;
import ru.builder.model.Equipment;
import ru.builder.model.document.Document;
import ru.builder.model.subscr.SubscriptionType;
import ru.builder.redis.RedisEntity;

import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class StatisticBean implements Statistic {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Inject
    private SubscriberOperations subscriberOperations;
    @Inject
    private Firebase firebase;
    @Inject
    private RedisEntity redisEntity;

    @Override
    public void sendStatisticMessage(String userId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(Long.parseLong(userId))
                .text("Выберите параметр статистики, который Вас интересует 📈📉")
                .replyMarkup(getStatisticOptionButtons(userId))
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendStatisticMessage(String userId, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        try {
            new BuilderBot().execute(
                    EditMessageText.builder()
                            .chatId(Long.parseLong(userId))
                            .text("Выберите параметр статистики, который Вас интересует 📈📉")
                            .messageId(messageId)
                            .replyMarkup(getStatisticOptionButtons(userId))
                            .build()
            );
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountOfSubscriptionsForEachUser(String userId, String status, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        long count = subscriberOperations.getCountOfSubscriptionsForEachUser(status);
        String text = "Данных по статистике не найдено 🤷‍♂️";
        String typeSubscription = status.equals("Active") ? "платных" : "триальных";

        if (count != 0) {
            text = "Кол-во <b>" + typeSubscription + "</b> подписок на текущий момент - " + count + " шт.";
        }

        EditMessageText sendMessage = EditMessageText.builder()
                .chatId(Long.parseLong(userId))
                .text(text)
                .messageId(messageId)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getBackToMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountOfSubscriptionsForEachEquipment(String userId, String status, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        List<Equipment> equipmentList = subscriberOperations.getCountOfSubscriptionsForEachEquipment(status);


        // Получаем из БД Firebase все виды спецтехники
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();
        equipmentList.forEach(equipment -> {
            // Получаем имя спецтехники по ID и записываем в объект equipment
            Document document = heavyTypes.stream().filter(heavyType -> heavyType.getFields().getId().getStringValue().equals(equipment.getId())).findFirst().orElse(null);
            String equipmentName = document != null ? document.getFields().getName().getStringValue() : "";
            equipment.setName(equipmentName);
        });
        // Сортируем по алфавиту наименования спецтехники
        equipmentList.sort(Comparator.comparing(object -> object.getName()));

        String text = "Данных по статистике не найдено 🤷‍♂️";
        String typeSubscription = status.equals("Active") ? "платных" : "триальных";

        StringBuilder sb = new StringBuilder();

        equipmentList.forEach(equipment -> {
            sb.append("<b>").append(equipment.getName()).append("</b>")
                    .append(" - ")
                    .append(equipment.getCountOfSubscription()).append(" шт.").append("\n");
        });

        sb.insert(0, "Кол-во <b>" + typeSubscription + "</b> подписок по конкретной спецтехнике на текущий момент\n\n");

        if (!equipmentList.isEmpty())
            text = sb.toString();

        EditMessageText sendMessage = EditMessageText.builder()
                .chatId(Long.parseLong(userId))
                .text(text)
                .messageId(messageId)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getBackToMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountAndDurationOfPaidSubscriptionsForEachEquipment(String userId, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        List<Equipment> equipmentList = subscriberOperations.getCountAndDurationOfPaidSubscriptionsForEachEquipment();

        // Получаем из БД Firebase все виды спецтехники
        List<Document> heavyTypes = firebase.getAllHeavyMachineryName();
        equipmentList.forEach(equipment -> {
            // Получаем имя спецтехники по ID и записываем в объект equipment
            Document document = heavyTypes.stream().filter(heavyType -> heavyType.getFields().getId().getStringValue().equals(equipment.getId())).findFirst().orElse(null);
            String equipmentName = document != null ? document.getFields().getName().getStringValue() : "";
            equipment.setName(equipmentName);
        });
        // Сортируем по алфавиту наименования спецтехники
        equipmentList.sort(Comparator.comparing(object -> object.getName()));

        String text = "Данных по статистике не найдено 🤷‍♂️";
        StringBuilder sb = new StringBuilder();

        equipmentList.forEach(equipment -> {

            String duration = "";

            if (equipment.getDuration().equals(SubscriptionType.week.value())) {
                duration = "Неделя";
            } else if (equipment.getDuration().equals(SubscriptionType.month.value())) {
                duration = "Месяц";
            } else if (equipment.getDuration().equals(SubscriptionType.year.value())) {
                duration = "Год";
            } else if (equipment.getDuration().equals(SubscriptionType.test.value())) {
                duration = "Тестовая подписка на 5 мин.";
            }

            sb.append("<b>").append(equipment.getName()).append("</b>")
                    .append("/")
                    .append(duration)
                    .append(" - ")
                    .append(equipment.getCountOfSubscription()).append(" шт.").append("\n");
        });

        sb.insert(0, "<b>Кол-во и длительность платных подписок</b> по конкретной спецтехнике на текущий момент\n\n");

        if (!equipmentList.isEmpty())
            text = sb.toString();

        EditMessageText sendMessage = EditMessageText.builder()
                .chatId(Long.parseLong(userId))
                .text(text)
                .messageId(messageId)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getBackToMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountOfNewBotUsers(String userId, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        int countOfNewBotUsers = 0;

        if (redisEntity.getElements("new_users") != null
                && !redisEntity.getElements("new_users").isEmpty())
            countOfNewBotUsers = redisEntity.getElements("new_users").size();

        EditMessageText sendMessage = EditMessageText.builder()
                .chatId(Long.parseLong(userId))
                .text("Новых запусков бота за текущий день - " + countOfNewBotUsers)
                .messageId(messageId)
                .replyMarkup(getBackToMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountOfYaUsers(String userId, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        int countOfYaUsers = 0;
        int countOfYaUsersToday = 0;

        if (redisEntity.getElements("new_users_yandex") != null
                && !redisEntity.getElements("new_users_yandex").isEmpty())
            countOfYaUsers = redisEntity.getElements("new_users_yandex").size();

        if (redisEntity.getElements("new_users_yandex_today") != null
                && !redisEntity.getElements("new_users_yandex_today").isEmpty())
            countOfYaUsersToday = redisEntity.getElements("new_users_yandex_today").size();

        try {
            new BuilderBot().execute(EditMessageText.builder()
                    .chatId(Long.parseLong(userId))
                    .text("Кол-во пользователей по реферальной ссылке из Яндекса:\n\n" +
                            "Всего - " + countOfYaUsers + "\n" +
                            "Сегодня - " + countOfYaUsersToday
                    )
                    .messageId(messageId)
                    .replyMarkup(getBackToMainMenuButton())
                    .build());
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void getCountOfBotUsers(String userId, Integer messageId) {
        this.logEditMessage(Long.parseLong(userId), messageId);
        long countOfBotUsers = subscriberOperations.getCountOfBotUsers();

        EditMessageText sendMessage = EditMessageText.builder()
                .chatId(Long.parseLong(userId))
                .text("Кол-во пользователей бота - " + countOfBotUsers)
                .messageId(messageId)
                .replyMarkup(getBackToMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private InlineKeyboardMarkup getBackToMainMenuButton() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("<< Назад");
        inlineKeyboardButton.setCallbackData("back_to_main_menu");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);
        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getStatisticOptionButtons(String userId) {

        boolean isAdmin = redisEntity.getElement(userId.concat("_isAdmin")) != null
                && !redisEntity.getElement(userId.concat("_isAdmin")).isEmpty()
                && redisEntity.getElement(userId.concat("_isAdmin")).equals("true");

        boolean isYaAdmin = redisEntity.getElement(userId.concat("_isYaAdmin")) != null
                && !redisEntity.getElement(userId.concat("_isYaAdmin")).isEmpty()
                && redisEntity.getElement(userId.concat("_isYaAdmin")).equals("true");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfListInlineKey = new ArrayList<>();
        List<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();

        // Если пользователь админ, но при этом у него еще есть флажок isYaAdmin (Для таргетолога),
        // то отображаем только кнопку для статистики по Яндексу
        if (isYaAdmin && isAdmin) {
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            inlineKeyboardButton.setText("Кол-во пользователей из Яндекса");
            inlineKeyboardButton.setCallbackData("count_of_ya_user");
            inlineKeyboardButtons.add(inlineKeyboardButton);
            listOfListInlineKey.add(inlineKeyboardButtons);

            inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);
            return inlineKeyboardMarkup;
        }

        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Общее кол-во триальных подписок");
        inlineKeyboardButton.setCallbackData("count_of_trial_subscr");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Общее кол-во платных подпискок");
        inlineKeyboardButton.setCallbackData("count_of_paid_subscr");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Триальные подписки по спецтехнике");
        inlineKeyboardButton.setCallbackData("count_of_trial_subscr_by_equipment");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Платные подписки по спецтехнике");
        inlineKeyboardButton.setCallbackData("count_of_paid_subscr_by_equipment");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Кол-во и длительность платных подписок по спецтехнике");
        inlineKeyboardButton.setCallbackData("count_and_duration_of_paid_subscr_by_equipment");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Новых запусков бота за текущий день");
        inlineKeyboardButton.setCallbackData("count_of_new_user_by_day");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Всего пользователей");
        inlineKeyboardButton.setCallbackData("count_of_user");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardButtons = new ArrayList<>();
        inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Кол-во пользователей из Яндекса");
        inlineKeyboardButton.setCallbackData("count_of_ya_user");
        inlineKeyboardButtons.add(inlineKeyboardButton);
        listOfListInlineKey.add(inlineKeyboardButtons);

        inlineKeyboardMarkup.setKeyboard(listOfListInlineKey);

        return inlineKeyboardMarkup;
    }

    private void logEditMessage(Long userId, Integer messageId) {
        logger.log(Level.SEVERE, "Edit message (statistic): userId - {0}, messageId - {1}", new Object[]{userId, messageId});
    }

}
