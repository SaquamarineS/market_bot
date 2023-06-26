package ru.builder.db.document;

import com.google.gson.Gson;
import ru.builder.model.document.Document;
import ru.builder.model.document.Fields;
import ru.builder.model.fields.*;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.enterprise.inject.Default;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Stateless
public class DocumentDB implements DocumentOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Resource(name = "jdbc/PostgresDS", mappedName = "java:jboss/PostgresDS")
    private DataSource dataSource;

    @Override
    public void addDocument(Document document) {
        logger.log(Level.INFO, "Add new document with params: {0}", new Object[]{new Gson().toJson(document)});
        String insertQuery = "INSERT INTO documents (id, user_name, user_phone," +
                " created_at, comment, owner_id, address, type_id, latitude," +
                " longitude , payment_method, processed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(insertQuery)) {
            ps.setString(1, document.getFields().getId().getStringValue());
            ps.setString(2, document.getFields().getUserName().getStringValue());
            ps.setString(3, document.getFields().getUserPhone().getStringValue());
            ps.setString(4, document.getFields().getCreatedAt().getTimestampValue());
            ps.setString(5, document.getFields().getComment().getStringValue());
            ps.setString(6, document.getFields().getOwnerId().getStringValue());
            ps.setString(7, document.getFields().getAddress().getStringValue());
            ps.setString(8, document.getFields().getTypeID().getStringValue());
            ps.setString(9, document.getFields().getLocation().getGeoPoint().getLatitude());
            ps.setString(10, document.getFields().getLocation().getGeoPoint().getLongitude());
            ps.setString(11, document.getFields().getPaymentMethod().getStringValue());
            ps.setBoolean(12, false);
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public Document getDocumentById(String id) {
        logger.log(Level.INFO, "Get Document by id - {0}", id);
        Document document = null;
        String selectQuery = "SELECT * FROM documents WHERE id = ?;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.setString(1, id);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                document = new Document(
                        new Fields(
                                new UserName(resultSet.getString("user_name")),
                                new CreatedAt(resultSet.getString("created_at")),
                                new UserPhone(resultSet.getString("user_phone")),
                                new Comment(resultSet.getString("comment")),
                                new OwnerId(resultSet.getString("owner_id")),
                                new Address(resultSet.getString("address")),
                                new TypeID(resultSet.getString("type_id")),
                                new Location(new GeoPoint(resultSet.getString("latitude"), resultSet.getString("longitude"))),
                                new PaymentMethod(resultSet.getString("payment_method")),
                                new Id(resultSet.getString("id")))
                );
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return document;
    }

    @Override
    public List<Document> getFirstTwoNotProcessedDocuments() {
        logger.log(Level.INFO, "Get first two not processed documents ...");
        List<Document> documentList = null;
        String selectQuery = "SELECT * FROM documents WHERE processed = FALSE order by created_at limit 2;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ResultSet resultSet = ps.executeQuery();
            documentList = new ArrayList<>();
            while (resultSet.next()) {
                documentList.add(new Document(
                        new Fields(
                                new UserName(resultSet.getString("user_name")),
                                new CreatedAt(resultSet.getString("created_at")),
                                new UserPhone(resultSet.getString("user_phone")),
                                new Comment(resultSet.getString("comment")),
                                new OwnerId(resultSet.getString("owner_id")),
                                new Address(resultSet.getString("address")),
                                new TypeID(resultSet.getString("type_id")),
                                new Location(new GeoPoint(resultSet.getString("latitude"), resultSet.getString("longitude"))),
                                new PaymentMethod(resultSet.getString("payment_method")),
                                new Id(resultSet.getString("id")))
                ));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return documentList;
    }

    @Override
    public void markDocumentAsProcessed(String id) {
        logger.log(Level.INFO, "Mark documentId - {0} as processed", id);
        String insertQuery = "UPDATE documents SET processed = TRUE WHERE id = ?; ";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(insertQuery)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean checkDocumentId(String documentId) {
        logger.log(Level.INFO, "Check documentId - {0}", documentId);
        String selectQuery = "SELECT * FROM documents WHERE id = ?;";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.setString(1, documentId);
            ResultSet resultSet = ps.executeQuery();
            return resultSet.next();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public void deleteOlderDocuments(String documentIds) {
        logger.log(Level.INFO, "Delete older documents by documentIds - {0}", documentIds);
        String selectQuery = "DELETE FROM documents WHERE id IN " + documentIds + ";";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(selectQuery)) {
            ps.execute();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}