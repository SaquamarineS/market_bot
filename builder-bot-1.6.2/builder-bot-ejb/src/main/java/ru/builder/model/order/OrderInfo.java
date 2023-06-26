package ru.builder.model.order;

public class OrderInfo {

    private String chatId;
    private String documentId;
    private String messageId;
    private String date;

    public OrderInfo(String chatId, String documentId, String messageId, String date) {
        this.chatId = chatId;
        this.documentId = documentId;
        this.messageId = messageId;
        this.date = date;
    }

    public OrderInfo(String chatId, String documentId, String messageId) {
        this.chatId = chatId;
        this.documentId = documentId;
        this.messageId = messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getDate() {
        return date;
    }
}
