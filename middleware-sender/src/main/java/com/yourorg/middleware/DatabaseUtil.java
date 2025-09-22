package com.yourorg.middleware;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseUtil {
    private static final String URL = "jdbc:postgresql://localhost:5432/alertsdb";
    private static final String USER = "alerts_user";   // adjust if needed
    private static final String PASSWORD = "alerts123"; // adjust if needed

    static {
        try {
            Class.forName("org.postgresql.Driver"); // load JDBC driver
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found!", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
