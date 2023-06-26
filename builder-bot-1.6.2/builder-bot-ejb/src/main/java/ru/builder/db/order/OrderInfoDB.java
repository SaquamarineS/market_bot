package ru.builder.db.order;

import com.google.gson.Gson;
import ru.builder.model.order.OrderInfo;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.sql.DataSource;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class OrderInfoDB implements OrderInfoOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource(name = "jdbc/PostgresDS", mappedName = "java:jboss/PostgresDS")
    private DataSource dataSource;

    @Override
    public void addTmpInfo(OrderInfo orderInfo, String docDate) {
        logger.log(Level.INFO, "Add new OrderInfo - {0}", new Gson().toJson(orderInfo));
        String insertQuery = "INSERT INTO order_info (chat_id, document_id, message_id, date, doc_date) VALUES (?, ?, ?, ?, ?);";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(insertQuery)) {
            ps.setString(1, orderInfo.getChatId());
            ps.setString(2, orderInfo.getDocumentId());
            ps.setString(3, orderInfo.getMessageId());
            ps.setString(4, dateFormat.format(new Timestamp(System.currentTimeMillis())));
            ps.setString(5, docDate);
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String getDocumentIdByChatIdAndMessageId(String chatId, String messageId) {
        logger.log(Level.INFO, "Get documentId by chatId - {0} and messageId - {1}", new Object[]{chatId, messageId});
        String selectQuery = "SELECT * FROM order_info WHERE chat_id = ? AND message_id = ?;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.setString(1, chatId);
            ps.setString(2, messageId);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("document_id");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public String getAllOrdersByDate(String dateMinus5Hours) {
        logger.log(Level.INFO, "Get all Orders with date <= {0}", dateMinus5Hours);
        StringBuilder stringBuilder = new StringBuilder();
        String selectQuery = "SELECT DISTINCT document_id FROM order_info WHERE date::timestamp < ?::timestamp;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.setString(1, dateMinus5Hours);
            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                stringBuilder.append("'")
                        .append(resultSet.getString("document_id"))
                        .append("'").append(",");
            }
            if (stringBuilder.toString().isEmpty())
                return null;
            stringBuilder.insert(0, "(");
            return stringBuilder.substring(0, stringBuilder.toString().lastIndexOf(',')).concat(")");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public List<String> getAllUniqueDocumentIds() {
        logger.log(Level.INFO, "Get all unique documents from order_info ...");
        List<String> documentList = null;
        String selectQuery = "SELECT distinct document_id FROM order_info;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ResultSet resultSet = ps.executeQuery();
            documentList = new ArrayList<>();
            while (resultSet.next()) {
                documentList.add(resultSet.getString("document_id"));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return documentList;
    }

    @Override
    public void deleteOlderOrders(String documentIds) {
        logger.log(Level.INFO, "Delete Orders from DB with documentIds - {0}", documentIds);
        String selectQuery = "DELETE FROM order_info WHERE document_id IN " + documentIds + ";";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.execute();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

}
