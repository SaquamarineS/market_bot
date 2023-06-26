package ru.builder.model.post;

public class ChannelPost {

    private String userId;
    private String messageId;
    private String channelId;

    public ChannelPost(String userId, String messageId, String channelId) {
        this.userId = userId;
        this.messageId = messageId;
        this.channelId = channelId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getUserId() {
        return userId;
    }

    public String getMessageId() {
        return messageId;
    }

}
