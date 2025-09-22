package com.yourorg.middleware;

public class QueryDemo {
    public static void main(String[] args) {
        WazuhAlertDao dao = new WazuhAlertDao();
        dao.findBySeverity("high");   // query only high severity alerts
    }
}
