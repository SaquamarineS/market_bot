package ru.builder.db.subscription;


import ru.builder.db.DBConnection;
import ru.builder.model.subscr.Subscription;
import ru.builder.model.subscr.SubscriptionType;

import javax.ejb.AccessTimeout;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Singleton
@AccessTimeout(15000)
public class SubscriptionDB implements SubscriptionOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Inject
    private DBConnection connection;

    @Override
    public void createTable() {
        logger.log(Level.SEVERE, "Create table Subscription ...");
        String createTableQuery = "create table if not exists Subscription (type varchar, equipmentType varchar, amount varchar, description varchar, duration int);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table Subscription done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @Lock(LockType.READ)
    public Subscription getSubscriptionInfo(String subscriptionType, String equipmentType) {
        logger.log(Level.INFO, "Get Subscription info by subscriptionType - {0} and equipmentType - {1}", new Object[]{subscriptionType, equipmentType});
        Subscription subscription = null;
        String selectQuery = "select * from Subscription where type = ? and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, subscriptionType);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                subscription = new Subscription(
                        resultSet.getString("amount"),
                        resultSet.getString("description"),
                        SubscriptionType.valueOf(resultSet.getString("type")),
                        resultSet.getInt("duration"));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        if (subscription == null) {
            logger.log(Level.SEVERE, "subscription is null!");
            return null;
        }
        logger.log(Level.INFO, "Subscription amount - {0}, description - {1}, type - {2}", new Object[]{subscription.getAmount(), subscription.getDescription(), subscription.getSubscriptionType()});

        return subscription;
    }

    @Override
    @Lock(LockType.READ)
    public List<Subscription> getAllSubscriptions(String equipmentId) {
        logger.log(Level.INFO, "Get all Subscriptions...");
        List<Subscription> subscriptionsList = null;
        String selectQuery = "select * from Subscription where equipmentType = ?";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, equipmentId);
            ResultSet resultSet = statement.executeQuery();
            subscriptionsList = new ArrayList<>();
            while (resultSet.next()) {
                subscriptionsList.add(new Subscription(
                        resultSet.getString("amount"),
                        resultSet.getString("description"),
                        SubscriptionType.valueOf(resultSet.getString("type")),
                        resultSet.getInt("duration")));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return subscriptionsList;
    }
}