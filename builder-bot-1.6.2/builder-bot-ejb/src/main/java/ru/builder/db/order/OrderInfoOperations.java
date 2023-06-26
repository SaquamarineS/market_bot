package ru.builder.db.order;

import ru.builder.model.order.OrderInfo;

import java.util.List;

public interface OrderInfoOperations {

    void addTmpInfo(OrderInfo orderInfo, String docDate);

    String getDocumentIdByChatIdAndMessageId(String chatId, String messageId);

    String getAllOrdersByDate(String date);

    List<String> getAllUniqueDocumentIds();

    void deleteOlderOrders(String documentIds);
}
