/**
 * Этот класс представляет слушатель сообщений для восьмой очереди сообщений.
 * Он используется для получения сообщений из очереди и обработки их данных.
 */
package ru.builder.bean.queue;

import ru.builder.bean.general.General;
import ru.builder.model.order.Order;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType",
                propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destinationLookup",
                propertyValue = "jms/queue/eighthQueue"),
        @ActivationConfigProperty(propertyName = "maxSession",
                propertyValue = "40")
})
public class EighthMDBBean implements MessageListener {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Inject
    private General general;

    /**
     * Метод обработки полученного сообщения из очереди.
     *
     * @param message Полученное сообщение
     */
    @Override
    public void onMessage(Message message) {
        try {
            // Извлекаем свойство "userId" из сообщения
            String userId = message.getStringProperty("userId");
            
            // Преобразуем полученное сообщение в объект типа ObjectMessage
            ObjectMessage objectMessage = (ObjectMessage) message;
            
            // Извлекаем объект из сообщения
            Object object = objectMessage.getObject();
            
            // Преобразуем объект в тип Order
            Order order = (Order) object;
            
            // Логируем информацию о переданном заказе и userId
            logger.log(Level.INFO, "Send order to userId - {0}", userId);
            
            // Вызываем метод отправки заказа пользователю в общем компоненте
            general.sendOrderToUser(order, userId);
        } catch (Exception ex) {
            // Обрабатываем возможные исключения и логируем ошибку
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
