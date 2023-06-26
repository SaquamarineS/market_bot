package ru.builder.db.connection;

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

/**
 * Класс Connections предоставляет операции для работы с подключениями к базе данных.
 */
@Default
@Singleton
@AccessTimeout(15000)
public class Connections implements ConnectionOperations {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Inject
    private DBConnection connection;

    /**
     * Метод createTable() используется для создания таблицы Connections в базе данных.
     */
    @Override
    public void createTable() {
        logger.log(Level.SEVERE, "Create table Connections ...");
        String createTableQuery = "create table if not exists Connections (equipmentType varchar primary key, equipmentName varchar, countOfConnections int);";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(createTableQuery)) {
            statement.execute();
            logger.log(Level.SEVERE, "Create table Connections done ...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Метод getCountOfConnectionsByEquipmentType(String equipmentType) используется для получения количества
     * подключений по заданному типу оборудования.
     *
     * @param equipmentType Тип оборудования, для которого требуется получить количество подключений.
     * @return Количество подключений в виде целочисленного значения.
     */
    @Override
    @Lock(LockType.READ)
    public int getCountOfConnectionsByEquipmentType(String equipmentType) {
        logger.log(Level.INFO, "Get count of connections by equipmentType - {0}", equipmentType);
        int countOfConnections = 0;
        String selectQuery = "select countOfConnections from Connections where equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("countOfConnections");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return countOfConnections;
    }

    /**
     * Метод getNameByEquipmentType(String equipmentType) используется для получения имени, связанного с заданным
     * типом оборудования.
     *
     * @param equipmentType Тип оборудования, для которого требуется получить имя.
     * @return Имя, связанное с типом оборудования, в виде строки.
     */
    @Override
    public String getNameByEquipmentType(String equipmentType) {
        logger.log(Level.INFO, "Get equipment name by equipmentType - {0}", equipmentType);
        String selectQuery = "select equipmentName from Connections where equipmentType = ?;";
        try (PreparedStatement statement = connection.getConnection().prepareStatement(selectQuery)) {
            statement.setString(1, equipmentType);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("equipmentName");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
