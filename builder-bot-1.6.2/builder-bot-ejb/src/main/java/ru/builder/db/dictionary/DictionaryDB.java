package ru.builder.db.dictionary;

import ru.builder.db.DBConnection;

import javax.ejb.AccessTimeout;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Singleton
@AccessTimeout(15000)
public class DictionaryDB implements DictionaryOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Inject
    private DBConnection connection;

    @Override
    public void createTable() {
        logger.log(Level.SEVERE, "Create table Dictionary ...");
        String createTableQuery = "create table if not exists Dictionary (reason varchar, description varchar);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table Dictionary done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @Lock(LockType.READ)
    public String getErrorDescriptionByReason(String reason) {
        logger.log(Level.INFO, "Get error description by reason - {0}", reason);
        String description = null;
        String selectQuery = "select * from Dictionary where reason = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, reason);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                description = resultSet.getString("description");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (description == null) {
            logger.log(Level.SEVERE, "description is null!");
            return null;
        }

        return description;
    }
}