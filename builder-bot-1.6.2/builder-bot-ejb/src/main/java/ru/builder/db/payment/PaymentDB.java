package ru.builder.db.payment;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.builder.db.DBConnection;
import ru.builder.model.payment.Amount;
import ru.builder.model.payment.CreatePayment;
import ru.builder.model.payment.Payment;
import ru.builder.model.payment.PaymentMethod;
import ru.builder.model.payment.error.ErrorPayment;

import javax.ejb.AccessTimeout;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Default
@Singleton
@AccessTimeout(15000)
public class PaymentDB implements PaymentOperations {
    
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").toPattern());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Inject
    private DBConnection connection;

    @Override
    public void createTable() {
        logger.log(Level.SEVERE, "Create table Payments ...");
        String createTableQuery = "create table if not exists Payments (id varchar primary key, status varchar, createdAt varchar, capturedAt varchar, confirmation_url varchar, isPaid boolean, type varchar, methodId varchar, userId varchar, messageId varchar, cancellationDetails varchar, nextBillDate varchar, isAutoPayment boolean, description varchar, amount varchar);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table Payments done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void createErrorPaymentsTable() {
        logger.log(Level.SEVERE, "Create table ErrorPayments ...");
        String createTableQuery = "create table if not exists ErrorPayments (paymentMethodId varchar, request varchar, isAutoPay boolean, userId varchar, subscriptionType varchar, errorDate varchar, status varchar, tryCount int);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table ErrorPayments done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void addErrorPayment(String paymentMethodId, String request, boolean isAutoPay, String userId, String subscriptionType, String status) {
        logger.log(Level.INFO, "Add error payment for retrying: paymentMethodId - {0}, request - {1}, isAutoPay - {2}, userId - {3}, subscriptionType - {4}, status - {5}, errorDate - {6}", new Object[]{paymentMethodId, request, isAutoPay, userId, subscriptionType, status, dateFormat.format(Calendar.getInstance().getTime())});
        String insertQuery = "insert into ErrorPayments (paymentMethodId, request, isAutoPay, userId, subscriptionType, status, errorDate) values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(insertQuery)) {
            statement.setString(1, paymentMethodId);
            statement.setString(2, request);
            statement.setBoolean(3, isAutoPay);
            statement.setString(4, userId);
            statement.setString(5, subscriptionType);
            statement.setString(6, status);
            statement.setString(7, dateFormat.format(Calendar.getInstance().getTime()));
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void addPayment(Payment payment, String userId, String messageId) {
        logger.log(Level.INFO, "Add new payment: id - {0}, status - {1}, date - {2}, userId - {3}", new Object[]{payment.getId(), payment.getStatus(), payment.getCreatedAt(), userId});
        String insertQuery = "insert into Payments (id, status, createdAt, isPaid, confirmation_url, userId, description, amount, messageId) values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(insertQuery)) {
            statement.setString(1, payment.getId());
            statement.setString(2, payment.getStatus());
            statement.setString(3, convertDate(payment.getCreatedAt()));
            statement.setBoolean(4, payment.isPaid());
            statement.setString(5, payment.getConfirmation().getConfirmationUrl());
            statement.setString(6, userId);
            statement.setString(7, payment.getDescription());
            statement.setString(8, payment.getAmount().getValue());
            statement.setString(9, messageId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void addAutoPayment(Payment payment, String userId) {
        logger.log(Level.INFO, "Add auto payment info: id - {0}, status - {1}, date - {2}, userId - {3}", new Object[]{payment.getId(), payment.getStatus(), payment.getCreatedAt(), userId});
        String insertQuery = "insert into Payments (id, status, createdAt, capturedAt, isPaid, userId, methodId, nextBilLDate, isAutoPayment, type, description, amount, cancellationDetails) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(insertQuery)) {
            statement.setString(1, payment.getId());
            statement.setString(2, payment.getStatus());
            statement.setString(3, convertDate(payment.getCreatedAt()));
            statement.setString(4, convertDate(payment.getCapturedAt()));
            statement.setBoolean(5, payment.isPaid());
            statement.setString(6, userId);
            statement.setString(7, payment.getPaymentMethod().getId());
            statement.setString(8, payment.getNextBillDate() != null ? payment.getNextBillDate() : "");
            statement.setBoolean(9, true);
            statement.setString(10, payment.getPaymentMethod().getType());
            statement.setString(11, payment.getDescription());
            statement.setString(12, payment.getAmount().getValue());
            statement.setString(13, payment.getCancellationDetails() != null ? new Gson().toJson(payment.getCancellationDetails()) : "");
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setSucceededPaymentInfo(Payment payment) {
        logger.log(Level.INFO, "Put info about succeeded payment with paymentId - {0} ", payment.getId());
        String updateQuery = "update Payments set status = ?, isPaid = ?, capturedAt = ?, type = ?, methodId = ?, nextBilLDate = ? where id = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, payment.getStatus());
            statement.setBoolean(2, payment.isPaid());
            statement.setString(3, convertDate(payment.getCapturedAt()));
            statement.setString(4, payment.getPaymentMethod().getType());
            statement.setString(5, payment.getPaymentMethod().getId());
            statement.setString(6, payment.getNextBillDate());
            statement.setString(7, payment.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setCanceledPaymentInfo(Payment payment) {
        logger.log(Level.INFO, "Put info about canceled payment with paymentId - {0} ", payment.getId());
        String updateQuery = "update Payments set status = ?, cancellationDetails = ? where id = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, payment.getStatus());
            statement.setString(2, new Gson().toJson(payment.getCancellationDetails()));
            statement.setString(3, payment.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @Lock(LockType.READ)
    public List<String> getPaymentIdsWithPendingStatus() {
        logger.log(Level.INFO, "Get payment Ids with 'pending' status ...");
        List<String> paymentIds = null;
        String selectQuery = "select * from Payments where status = 'pending';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            paymentIds = new ArrayList<>();
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                paymentIds.add(resultSet.getString("id"));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return paymentIds;
    }

    @Override
    @Lock(LockType.READ)
    public Payment getPaymentByPaymentId(String id) {
        logger.log(Level.INFO, "Get Payment by id - {0}", id);
        String selectQuery = "select * from Payments where id = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                Payment payment = new Payment();
                payment.setId(resultSet.getString("id"));
                payment.setNextBillDate(resultSet.getString("nextBillDate"));
                payment.setDescription(resultSet.getString("description"));
                payment.setUserId(resultSet.getString("userId"));
                payment.setMessageId(resultSet.getString("messageId"));
                PaymentMethod paymentMethod = new PaymentMethod();
                paymentMethod.setId(resultSet.getString("methodId"));
                payment.setPaymentMethod(paymentMethod);

                Amount amount = new Amount();
                amount.setCurrency("RUB");
                amount.setValue(resultSet.getString("amount"));
                payment.setAmount(amount);

                return payment;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    @Lock(LockType.READ)
    public List<CreatePayment> getErrorPaymentsByStatus(String status) {
        logger.log(Level.INFO, "Get error payments for retrying with status - {0} ...", status);
        List<CreatePayment> errorPayments = null;
        String selectQuery = "select * from ErrorPayments where status = '" + status + "';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            ResultSet resultSet = statement.executeQuery();
            errorPayments = new ArrayList<>();
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            Gson gson = gsonBuilder.create();
            while (resultSet.next()) {
                CreatePayment createPayment = gson.fromJson(resultSet.getString("request"), CreatePayment.class);
                createPayment.setTryCount(resultSet.getInt("tryCount"));
                errorPayments.add(createPayment);
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return errorPayments;
    }

    @Override
    public String getErrorDateByPaymentMethodId(String paymentMethodId) {
        logger.log(Level.INFO, "Get errorDate by paymentMethodId - {0} and status - 'canceled'", paymentMethodId);
        String errorDate = null;
        String selectQuery = "select errorDate from ErrorPayments where paymentMethodId = ? and status = 'canceled';";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, paymentMethodId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                errorDate = resultSet.getString("errorDate");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return errorDate;
    }

    @Override
    public ErrorPayment getErrorInfoByPaymentMethodId(String paymentMethodId) {
        logger.log(Level.INFO, "Get errorDate and tryCount by paymentMethodId - {0} and status - canceled or wait", paymentMethodId);
        ErrorPayment errorPayment = null;
        String selectQuery = "select errorDate, tryCount from ErrorPayments where paymentMethodId = ? and (status = 'canceled' or status = 'wait');";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, paymentMethodId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return new ErrorPayment(
                        resultSet.getString("errorDate"),
                        resultSet.getInt("tryCount")
                );
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return errorPayment;
    }

    @Override
    public void setRetryCountForErrorAutoPayment(String paymentMethodId, int tryCount) {
        logger.log(Level.INFO, "Put tryCount value +1 for error auto payment with paymentMethodId - {0} ", paymentMethodId);
        String updateQuery = "update ErrorPayments set tryCount = ? where paymentMethodId = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setInt(1, tryCount + 1);
            statement.setString(2, paymentMethodId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setNewStatusForErrorAutoPayment(String paymentMethodId, String newStatus, String oldStatus) {
        logger.log(Level.INFO, "Put new status - {0} for error auto payment with paymentMethodId - {1} and old status - {2} ", new Object[]{newStatus, paymentMethodId, oldStatus});
        String updateQuery = "update ErrorPayments set status = ? where paymentMethodId = ? and status = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(updateQuery)) {
            statement.setString(1, newStatus);
            statement.setString(2, paymentMethodId);
            statement.setString(3, oldStatus);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
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
