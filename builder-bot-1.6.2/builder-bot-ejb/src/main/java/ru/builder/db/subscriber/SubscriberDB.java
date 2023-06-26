package ru.builder.db.subscriber;

import com.google.gson.Gson;
import ru.builder.db.DBConnection;
import ru.builder.model.Equipment;
import ru.builder.model.subscr.Subscriber;

import javax.ejb.AccessTimeout;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Singleton
@AccessTimeout(15000)
public class SubscriberDB implements SubscriberOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat.toPattern());

    @Inject
    private DBConnection connection;

    @Override
    public void createTable() {
        logger.log(Level.SEVERE, "Create table Subscribers ...");
        String createTableQuery = "create table if not exists Subscribers (id varchar, paymentId varchar primary key, email varchar, phone varchar, subscriptionType varchar, equipmentType varchar, status varchar, isAutoPay boolean, connectionCount int, date varchar, expiredDate varchar);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table Subscribers done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void addSubscriber(Subscriber subscriber) {
        logger.log(Level.INFO, "Add new subscriber - {0}", new Gson().toJson(subscriber));
        String insertQuery = "insert into Subscribers (id, equipmentType, connectionCount, status, date) values (?, ?, ?, ?, ?);";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        try (PreparedStatement statement = connection.getConnection().prepareStatement(insertQuery)) {
            statement.setString(1, subscriber.getUserId());
            statement.setString(2, subscriber.getEquipmentType());
            statement.setInt(3, subscriber.getConnectCount());
            statement.setString(4, "Trial");
            statement.setString(5, dateFormat.format(timestamp));
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void updateSubscriber(Subscriber subscriber) {
        logger.log(Level.INFO, "Update subscriber after create payment - {0}", new Gson().toJson(subscriber));
        String updateQuery = "update Subscribers set paymentId = ?, email = ?, subscriptionType = ? where id = ? and status = 'Trial' and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, subscriber.getPaymentId());
            statement.setString(2, subscriber.getEmail());
            statement.setString(3, subscriber.getSubscriptionType());
            statement.setString(4, subscriber.getUserId());
            statement.setString(5, subscriber.getEquipmentType());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void addSubscriberAfterAutoPayment(Subscriber subscriber, String date) {
        logger.log(Level.INFO, "Add new subscriber after auto payment - {0}", new Gson().toJson(subscriber));
        String insertQuery = "insert into Subscribers (id, paymentId, email, subscriptionType, equipmentType, status, date, isAutoPay) values (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(insertQuery)) {
            statement.setString(1, subscriber.getUserId());
            statement.setString(2, subscriber.getPaymentId());
            statement.setString(3, subscriber.getEmail());
            statement.setString(4, subscriber.getSubscriptionType());
            statement.setString(5, subscriber.getEquipmentType());
            statement.setString(6, "Active");
            statement.setString(7, convertDate(date));
            statement.setBoolean(8, true);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void updateSubscriptionInfo(String userId, String paymentId, String date, boolean isExpired) {
        String updateQuery;
        if (isExpired) {
            logger.log(Level.INFO, "Put subscription status 'Expired' and expiredDate - {0} for userId - {1} with paymentId - {2} ", new Object[]{date, userId, paymentId});
            updateQuery = "update Subscribers set status = 'Expired', expiredDate = ? where id = ? and paymentId = ?;";
        } else {
            logger.log(Level.INFO, "Put subscription status 'Active' and activation date - {0} for userId - {1} with paymentId - {2} ", new Object[]{convertDate(date), userId, paymentId});
            updateQuery = "update Subscribers set status = 'Active', date = ?, isAutoPay = ? where id = ? and paymentId = ?;";
        }
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            if (isExpired) {
                statement.setString(1, date);
                statement.setString(2, userId);
                statement.setString(3, paymentId);
            } else {
                statement.setString(1, convertDate(date));
                statement.setBoolean(2, true);
                statement.setString(3, userId);
                statement.setString(4, paymentId);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void updateSubscriptionInfo(String userId, String paymentId) {
        String updateQuery;
        logger.log(Level.INFO, "Change subscription status to 'AutoPay' for userId - {0} with paymentId - {1} ", new Object[]{userId, paymentId});
        updateQuery = "update Subscribers set status = 'AutoPay' where id = ? and paymentId = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, userId);
            statement.setString(2, paymentId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @Lock(LockType.READ)
    public List<Subscriber> getAllActiveSubscribers() {
        logger.log(Level.INFO, "Get all subscribers with status 'Active'");
        List<Subscriber> subscriberList = null;
        String selectQuery = "select * from Subscribers where status = 'Active';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            ResultSet resultSet = statement.executeQuery();
            subscriberList = new ArrayList<>();
            while (resultSet.next()) {
                subscriberList.add(new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("paymentId"),
                        resultSet.getString("email"),
                        resultSet.getString("subscriptionType"),
                        resultSet.getString("equipmentType"),
                        resultSet.getBoolean("isAutoPay")));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriberList;
    }

    @Override
    @Lock(LockType.READ)
    public Subscriber getSubscriberByPaymentId(String paymentId) {
        logger.log(Level.INFO, "Get Subscriber info by paymentId - {0}", paymentId);
        Subscriber subscriber = null;
        String selectQuery = "select * from Subscribers where paymentId = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, paymentId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                subscriber = new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("paymentId"),
                        resultSet.getString("email"),
                        resultSet.getString("subscriptionType"),
                        resultSet.getString("equipmentType"),
                        resultSet.getBoolean("isAutoPay"),
                        resultSet.getString("status"));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriber;
    }

    @Override
    @Lock(LockType.READ)
    public String getEquipmentTypeByPaymentId(String paymentId) {
        logger.log(Level.INFO, "Get equipmentId by paymentId - {0}", paymentId);
        String selectQuery = "select equipmentType from Subscribers where paymentId = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, paymentId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("subscriptionType");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    @Lock(LockType.READ)
    public Subscriber getSubscriberByIdAndEquipmentTypeAndActiveOrAutoPayStatus(String userId, String equipmentType) {
        logger.log(Level.INFO, "Get Subscriber info for userId - {0}, equipmentType - {1} and 'Active' or 'AutoPay' status ...", new Object[]{userId, equipmentType});
        Subscriber subscriber = null;
        String selectQuery = "select * from Subscribers where id = ? and equipmentType = ? and (status = 'Active' OR status = 'AutoPay');";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                subscriber = new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("paymentId"),
                        resultSet.getString("email"),
                        resultSet.getString("subscriptionType"),
                        resultSet.getString("equipmentType"),
                        resultSet.getBoolean("isAutoPay"),
                        resultSet.getString("status"));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriber;
    }

    @Override
    @Lock(LockType.READ)
    public Subscriber getActiveOrTrialSubscriberByIdAndEquipmentType(String userId, String equipmentType) {
        logger.log(Level.INFO, "Get Active or Trial subscriber info by id - {0} and equipmentType - {1}", new Object[]{userId, equipmentType});
        Subscriber subscriber = null;
        String selectQuery = "select * from Subscribers where id = ? and equipmentType = ? and (status = 'Active' OR status = 'Trial');";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                subscriber = new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("equipmentType"),
                        resultSet.getInt("connectionCount"),
                        resultSet.getString("status"),
                        resultSet.getString("paymentId"),
                        resultSet.getBoolean("isAutoPay")
                );
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriber;
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkIsActiveSubscriber(String userId, String equipmentType) {
        logger.log(Level.INFO, "Check is active subscriber with id - {0} and equipmentType - {1}", new Object[]{userId, equipmentType});
        String selectQuery = "select * from Subscribers where id = ? and equipmentType = ? and (status = 'Active' or status = 'AutoPay');";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.next();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "checkIsActiveSubscriber exception - {0}", ex.getMessage());
        }
        return false;
    }

    @Override
    public void changeConnectionCount(String userId, String equipmentType, int connectionCount) {
        logger.log(Level.INFO, "Update connection count for userId - {0} and typeId - {0}", new Object[]{userId, equipmentType});
        String updateQuery = "update Subscribers set connectionCount = ? where id = ? and equipmentType = ? and status = 'Trial';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setInt(1, connectionCount);
            statement.setString(2, userId);
            statement.setString(3, equipmentType);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void cancelPaidSubscription(String userId, String equipmentType) {
        logger.log(Level.INFO, "Cancel Active subscription for userId - {0} with equipmentType - {1}", new Object[]{userId, equipmentType});
        String updateQuery = "update Subscribers set isAutoPay = false where id = ? and equipmentType = ? and (status = 'Active' OR status = 'AutoPay');";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void cancelTrialSubscription(String userId, String equipmentType, boolean isBlocked) {
        logger.log(Level.INFO, "Cancel Trial subscription for userId - {0} with equipmentType - {1}", new Object[]{userId, equipmentType});
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String updateQuery = "update Subscribers set expiredDate = ?, status = 'Expired' where id = ? and equipmentType = ? and status = 'Trial';";

        // Если пользователь заблокировал бота, то проставляем подписке статус 'Pause',
        // чтобы потом при возвращении в бота восстановить эту подписку
        if (isBlocked) {
            updateQuery = "update Subscribers set expiredDate = ?, status = 'Pause' where id = ? and equipmentType = ? and status = 'Trial';";
        }

        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, dateFormat.format(timestamp));
            statement.setString(2, userId);
            statement.setString(3, equipmentType);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkActiveSubscription(String userId, String equipmentType) {
        logger.log(Level.INFO, "check active subscription for userId - {0}", userId);
        String selectQuery = "select * from Subscribers where status = 'Active' and id = ? and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkTrialSubscription(String userId, String equipmentType) {
        logger.log(Level.INFO, "check trial subscription for userId - {0}", userId);
        String selectQuery = "select * from Subscribers where status = 'Trial' and connectionCount = 0 and id = ? and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkAutoPaySubscription(String userId, String equipmentType) {
        logger.log(Level.INFO, "Check AutoPay subscription for userId - {0} and equipmentType - {1}", new Object[]{userId, equipmentType});
        String selectQuery = "select * from Subscribers where status = 'AutoPay' and id = ? and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkUnsubscribeUser(String userId) {
        logger.log(Level.INFO, "Check user is unsubscribe for userId - {0}", userId);
        String selectQuery = "select * from Subscribers where status = 'Active' and isAutoPay = false and id = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    @Lock(LockType.READ)
    public boolean checkSubscriberIsAlreadyExist(String userId) {
        logger.log(Level.INFO, "Check subscriber - {0} is already exist ...", userId);
        String selectQuery = "select * from Subscribers where id = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    @Lock(LockType.READ)
    public List<Subscriber> getAllActiveAndTrialSubscriptionsForUser(String userId) {
        logger.log(Level.INFO, "Get all active and trial subscriptions for userId - {0}", userId);
        List<Subscriber> subscribers = new ArrayList<>();
        String selectQuery = "select * from Subscribers where id = ? and ((status = 'Active' and isAutoPay = true) OR (status = 'AutoPay' and isAutoPay = true) OR (status = 'Trial' and isAutoPay is null));";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscribers.add(new Subscriber(
                                resultSet.getString("id"),
                                resultSet.getString("equipmentType"),
                                resultSet.getInt("connectionCount"),
                                resultSet.getString("status"),
                                resultSet.getString("paymentId"),
                                resultSet.getBoolean("isAutoPay")
                        )
                );
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscribers;
    }

    @Override
    public List<Subscriber> getAllActiveWithoutAutoPaySubscriptionsForUser(String userId) {
        logger.log(Level.INFO, "Get all Active subscriptions without auto pay for userId - {0}", userId);
        List<Subscriber> subscribers = new ArrayList<>();
        String selectQuery = "select * from Subscribers where id = ? and status = 'Active' and isAutoPay = false;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscribers.add(new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("equipmentType"),
                        resultSet.getInt("connectionCount"),
                        resultSet.getString("status"),
                        resultSet.getString("paymentId"),
                        resultSet.getBoolean("isAutoPay")
                ));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscribers;
    }

    @Override
    public void deleteInactiveUser(String userId, String equipmentType) {
        logger.log(Level.INFO, "Delete subscriber info with userId - {0} and equipmentType - {1}", new Object[]{userId, equipmentType});
        String deleteQuery = "delete from Subscribers where id = ? and equipmentType = ? and status = 'Trial';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(deleteQuery)) {
            statement.setString(1, userId);
            statement.setString(2, equipmentType);
            statement.execute();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    // === Restore subscription after restart the bot ===
    @Override
    public List<Subscriber> getPauseSubscriptions(String userId) {
        logger.log(Level.INFO, "Get all 'Pause' subscriptions for userId - {0}", userId);
        List<Subscriber> subscribers = new ArrayList<>();
        String selectQuery = "select * from Subscribers where id = ? and status = 'Pause';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscribers.add(new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("equipmentType"),
                        resultSet.getString("status")));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscribers;
    }

    @Override
    public void restoreTrialStatusForSubscription(Subscriber subscriber) {
        logger.log(Level.INFO, "Restore 'Trial' status for subscription - {0}", new Gson().toJson(subscriber));
        String updateQuery = "update Subscribers set status = 'Trial', expiredDate = null where id = ? and equipmentType = ? and status = 'Pause';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, subscriber.getUserId());
            statement.setString(2, subscriber.getEquipmentType());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public List<Subscriber> getOldInactiveUsers() {
        logger.log(Level.SEVERE, "Get OLD inactive users before 17 june (with null date and expiredDate)...");
        List<Subscriber> subscribers = new ArrayList<>();
        String selectQuery = "select * from Subscribers where status = 'Trial'" +
                " and email is null" +
                " and expiredDate is null" +
                " and date is null;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscribers.add(new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("equipmentType"),
                        resultSet.getString("status"),
                        resultSet.getString("date")
                ));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscribers;
    }

    @Override
    public void cancelInactiveOLDTrialSubscription(String userId, String equipmentType) {
        logger.log(Level.SEVERE, "Cancel inactive OLD Trial subscription for userId - {0} with equipmentType - {1}", new Object[]{userId, equipmentType});
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String updateQuery = "update Subscribers set expiredDate = ?, status = 'Expired' where status = 'Trial'" +
                " and email is null" +
                " and expiredDate is null" +
                " and date is null" +
                " and id = ?" +
                " and equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, dateFormat.format(timestamp));
            statement.setString(2, userId);
            statement.setString(3, equipmentType);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public List<Subscriber> getInactiveUsersForMoreThanFourMonths(String beforeDate) {
        logger.log(Level.SEVERE, "Get inactive users for more than four months ...");
        List<Subscriber> subscribers = new ArrayList<>();
        String selectQuery = "select * from Subscribers where email is null" +
                " and expiredDate is null" +
                " and status = 'Trial'" +
                " and (connectionCount < 20 or connectionCount > 9990)" +
                " and strftime('%s', date) < strftime('%s', ?);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, beforeDate);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscribers.add(new Subscriber(
                        resultSet.getString("id"),
                        resultSet.getString("equipmentType"),
                        resultSet.getString("status"),
                        resultSet.getString("date")
                ));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscribers;
    }

    @Override
    public void cancelInactiveTrialSubscription(String userId, String equipmentType, String beforeDate) {
        logger.log(Level.SEVERE, "Cancel inactive Trial subscription for userId - {0} with equipmentType - {1}", new Object[]{userId, equipmentType});
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String updateQuery = "update Subscribers set expiredDate = ?, status = 'Expired' where email is null" +
                " and expiredDate is null" +
                " and status = 'Trial'" +
                " and (connectionCount < 20 or connectionCount > 9990)" +
                " and id = ?" +
                " and equipmentType = ?" +
                " and strftime('%s', date) < strftime('%s', ?);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, dateFormat.format(timestamp));
            statement.setString(2, userId);
            statement.setString(3, equipmentType);
            statement.setString(4, beforeDate);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    // ======

    @Override
    @Lock(LockType.READ)
    public long getCountOfSubscriptionsForEachUser(String status) {
        logger.log(Level.INFO, "Get count of subscriptions with - {0} status ...", status);
        String selectQuery = "select count(*) as count from Subscribers where status = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, status);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong("count");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return 0L;
    }

    @Override
    @Lock(LockType.READ)
    public List<Equipment> getCountOfSubscriptionsForEachEquipment(String status) {
        logger.log(Level.INFO, "Get count of subscriptions for each equipment with - {0} status ...", status);
        List<Equipment> subscriberList = new ArrayList<>();
        String selectQuery = "select equipmentType, count(*) as count from Subscribers where status = ? group by equipmentType;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, status);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscriberList.add(new Equipment(resultSet.getString("equipmentType"), resultSet.getLong("count")));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriberList;
    }

    @Override
    @Lock(LockType.READ)
    public List<Equipment> getCountAndDurationOfPaidSubscriptionsForEachEquipment() {
        logger.log(Level.INFO, "Get count and duration of PAID subscriptions for each equipment ...");
        List<Equipment> subscriberList = new ArrayList<>();
        String selectQuery = "select equipmentType, subscriptionType, count(*) as count from Subscribers where status = 'Active' group by subscriptionType, equipmentType;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                subscriberList.add(new Equipment(resultSet.getString("equipmentType"), resultSet.getString("subscriptionType"), resultSet.getLong("count")));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return subscriberList;
    }

    @Override
    public long getCountOfBotUsers() {
        logger.log(Level.INFO, "Get count of ALL users ...");
        String selectQuery = "select count(distinct id) as count from Subscribers;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong("count");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return 0L;
    }

    private String convertDate(String utcDate) {
        logger.log(Level.INFO, "Input utcDate - {0}", utcDate);
        if (utcDate == null) {
            logger.log(Level.INFO, "Don't converting date, because value is null...");
            return null;
        }
        Instant timestamp = Instant.parse(utcDate);
        ZonedDateTime europeDateTime = timestamp.atZone(ZoneId.of("Europe/Moscow"));
        String convertedDate = europeDateTime.format(formatter);
        logger.log(Level.INFO, "Converted Date - {0}", convertedDate);
        return convertedDate;
    }
}
