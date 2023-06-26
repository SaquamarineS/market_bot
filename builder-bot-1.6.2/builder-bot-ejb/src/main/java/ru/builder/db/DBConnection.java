package ru.builder.db;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
@AccessTimeout(30000)
public class DBConnection {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());
    private Connection connection;

    @PostConstruct
    public void initialize() {
        try {
            logger.log(Level.SEVERE, "Initial datasource and connection...");
            DataSource dataSource = (DataSource) new InitialContext().lookup("java:jboss/datasources/SqliteDS");
            connection = dataSource.getConnection();
            logger.log(Level.SEVERE, "Initial done.");
        } catch (SQLException | NamingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Lock(LockType.READ)
    public Connection getConnection() {
        return this.connection;
    }

    @PreDestroy
    public void cleanup() {
        try {
            logger.log(Level.SEVERE, "Close connection...");
            connection.close();
            connection = null;
            logger.log(Level.SEVERE, "Connection was closed...");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}