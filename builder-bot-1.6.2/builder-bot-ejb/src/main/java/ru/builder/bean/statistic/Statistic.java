package ru.builder.bean.statistic;

public interface Statistic {

    void sendStatisticMessage(String userId);

    void sendStatisticMessage(String userId, Integer messageId);

    // Сколько всего триальных/платных подписок на текущий момент
    void getCountOfSubscriptionsForEachUser(String userId, String status, Integer messageId);

    // Сколько триальных/платных подписок по конкретной спецтехнике на текущий момент
    void getCountOfSubscriptionsForEachEquipment(String userId, String status, Integer messageId);

    // Сколько платных подписок и какая длительность подписки по конкретной спецтехнике на текущий момент
    void getCountAndDurationOfPaidSubscriptionsForEachEquipment(String userId, Integer messageId);

    // Кол-во новых запусков бота за текущий день
    void getCountOfNewBotUsers(String userId, Integer messageId);

    // Кол-во пользователей из Яндекса
    void getCountOfYaUsers(String userId, Integer messageId);

    // Общее кол-во пользователей
    void getCountOfBotUsers(String userId, Integer messageId);
}
