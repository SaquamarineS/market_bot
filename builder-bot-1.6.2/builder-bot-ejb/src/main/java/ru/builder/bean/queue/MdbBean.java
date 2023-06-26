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
                propertyValue = "jms/queue/ordersQueue"),
        @ActivationConfigProperty(propertyName = "maxSession",
                propertyValue = "40")
})
public class MdbBean implements MessageListener {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Inject
    private General general;

    /**
     * Метод, который обрабатывает сообщение из очереди `ordersQueue`.
     *
     * @param message полученное сообщение
     */
    @Override
    public void onMessage(Message message) {
        try {
            String userId = message.getStringProperty("userId");
            ObjectMessage objectMessage = (ObjectMessage) message;
            Object object = objectMessage.getObject();
            Order order = (Order) object;
            logger.log(Level.INFO, "Отправляем заказ пользователю с идентификатором - {0}", userId);
            general.sendOrderToUser(order, userId);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

}
