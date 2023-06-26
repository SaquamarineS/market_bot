/**
 * Этот класс реализует интерфейс Admin и предоставляет методы для взаимодействия с администратором.
 */
package ru.builder.bean.admin;

import com.google.gson.Gson;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.builder.bot.BuilderBot;
import ru.builder.model.post.ChannelPost;
import ru.builder.redis.RedisEntity;

import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class AdminBean implements Admin {

    @Inject
    private RedisEntity redisEntity;

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Override
    public void sendErrorMessagesToTechChannel(String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(System.getenv("builder_tech_channelId"))
                    .text(message)
                    .build();
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void sendFeedbackMessageToTechChannel(Message message) {
        String toChatId = System.getenv("builder_tech_channelId");

        ForwardMessage forwardMessage = ForwardMessage.builder()
                .chatId(toChatId)
                .fromChatId(String.valueOf(message.getFrom().getId()))
                .messageId(message.getMessageId())
                .build();

        try {
            Message channelMessage = new BuilderBot().execute(forwardMessage);

            ChannelPost channelPost = new ChannelPost(
                    String.valueOf(message.getFrom().getId()),
                    String.valueOf(channelMessage.getMessageId()),
                    toChatId
            );

            redisEntity.pushElement("builder_posts", new Gson().toJson(channelPost));
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void sendFinalMessage(String chatId) {
        try {
            new BuilderBot().execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Ваш запрос принят! С Вами свяжутся в ближайшее время")
                    .replyMarkup(getMainMenuButton())
                    .build()
            );
        } catch (TelegramApiException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        redisEntity.setElement(chatId.concat("_help"), "false");
    }

    @Override
    public void sendMessageFromChannel(Message channelReplyMessage) {
        String elem = null;
        List<String> posts = null;

        // Ищем пост по messageId и channelId
        if (redisEntity.getElements("builder_posts") != null) {
            posts = redisEntity.getElements("builder_posts");
            elem = posts
                    .stream()
                    .filter(post -> new Gson().fromJson(post, ChannelPost.class).getChannelId().equals(String.valueOf(channelReplyMessage.getChat().getId()))
                            && new Gson().fromJson(post, ChannelPost.class).getMessageId().equals(String.valueOf(channelReplyMessage.getReplyToMessage().getMessageId())))
                    .reduce((first, second) -> first).orElse(null);
        }

        // Если постов нет вообще или нужный пост не найден - ничего не делаем
        if (posts == null || posts.isEmpty()) {
            logger.log(Level.SEVERE, "List of posts is null or is empty! Don't sending answer to user ...");
            return;
        }
        if (elem == null || elem.isEmpty()) {
            logger.log(Level.SEVERE, "Post by filters is null or is empty! Don't sending answer to user ...");
            return;
        }

        // Получаем информацию о посте из json структуры
        ChannelPost userPost = new Gson().fromJson(elem, ChannelPost.class);
        logger.log(Level.SEVERE, "Send reply for MessageId - {0} to User - {1}", new Object[]{userPost.getMessageId(), userPost.getUserId()});

        // Обработка текста
        if (channelReplyMessage.getText() != null) {
            try {
                new BuilderBot().execute(
                        SendMessage.builder()
                                .text("<b>Поддержка: </b>" + channelReplyMessage.getText())
                                .parseMode(ParseMode.HTML)
                                .chatId(userPost.getUserId())
                                .replyMarkup(getCloseDialogButton())
                                .build()
                );
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }

        // Обработка фото
        if (channelReplyMessage.getPhoto() != null
                && !channelReplyMessage.getPhoto().isEmpty()) {
            logger.log(Level.SEVERE, "Send replay photo for MessageId - {0} to User - {1}", new Object[]{userPost.getMessageId(), userPost.getUserId()});
            InputFile inputFile = new InputFile();
            inputFile.setMedia(channelReplyMessage.getPhoto().get(0).getFileId());
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(userPost.getUserId())
                    .photo(inputFile)
                    .caption(channelReplyMessage.getCaption() != null ? "<b>Поддержка: </b>" + channelReplyMessage.getCaption() : "<b>Поддержка</b>")
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(getCloseDialogButton())
                    .build();
            try {
                new BuilderBot().execute(sendPhoto);
            } catch (TelegramApiException e) {
                logger.log(Level.SEVERE, null, e);
            }
            return;
        }

        // Устанавливаем для пользователя флажок, в котором будет указан id канала, куда отправлять ответы на сообшения админа
        String fromChannelId = System.getenv("builder_tech_channelId");
        redisEntity.setElement(userPost.getUserId().concat("_from_chatId"), fromChannelId);
    }

    @Override
    public void cancelingDialog(String chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text("Диалог с поддержкой закрыт ❌")
                .replyMarkup(getMainMenuButton())
                .build();
        try {
            new BuilderBot().execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private ReplyKeyboardMarkup getMainMenuButton() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(
                        Collections.singletonList(
                                new KeyboardButton("Главное меню 🎯")
                        )))
                .resizeKeyboard(true)
                .build();
    }

    private ReplyKeyboardMarkup getCloseDialogButton() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(
                        Collections.singletonList(new KeyboardButton("Закрыть диалог ❌"))))
                .resizeKeyboard(true)
                .build();
    }
}
