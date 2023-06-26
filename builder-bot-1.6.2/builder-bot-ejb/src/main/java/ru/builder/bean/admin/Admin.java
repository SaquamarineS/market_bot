/**
 * Этот интерфейс определяет методы для взаимодействия с администратором.
 */
package ru.builder.bean.admin;

import org.telegram.telegrambots.meta.api.objects.Message;

public interface Admin {

    /**
     * Отправляет сообщение об ошибке в технический канал.
     *
     * @param message Сообщение об ошибке
     */
    void sendErrorMessagesToTechChannel(String message);

    /**
     * Отправляет сообщение обратной связи в технический канал.
     *
     * @param message Сообщение обратной связи
     */
    void sendFeedbackMessageToTechChannel(Message message);

    /**
     * Отправляет окончательное сообщение в указанный чат.
     *
     * @param chatId Идентификатор чата
     */
    void sendFinalMessage(String chatId);

    /**
     * Отправляет сообщение из канала.
     *
     * @param channelReplyMessage Сообщение из канала
     */
    void sendMessageFromChannel(Message channelReplyMessage);

    /**
     * Отменяет диалог с указанным чатом.
     *
     * @param chatId Идентификатор чата
     */
    void cancelingDialog(String chatId);

}
