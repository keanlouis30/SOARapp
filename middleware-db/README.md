# Middleware for Alerts (PostgreSQL + Java)

This project is a simple **middleware** that accepts JSON alerts (e.g., from Wazuh), stores them in **PostgreSQL**, and allows you to query them later.  
It demonstrates how to bridge a security monitoring tool with a database backend.

---

## 📌 Why PostgreSQL?
- **Open Source & Free** – 100% free, enterprise-grade, no licensing cost.  
- **JSON/JSONB Support** – Perfect for storing alerts in their native JSON format.  
- **Robust & Scalable** – Widely used in production for security, logging, and analytics.  
- **SQL + JSON** – You can query structured fields inside the JSON (e.g., severity, host_id).  

Example query:
```sql
SELECT event_id, payload->>'severity'
FROM wazuh_alerts
WHERE payload->>'alert_type' = 'ransomware_detection';
```

---

## 🛠️ Install PostgreSQL

### 1. Download
Get the installer here:  
👉 [EnterpriseDB PostgreSQL Downloads](https://www.enterprisedb.com/downloads/postgres-postgresql-downloads)

### 2. Install
- Run the installer.  
- Choose:
  - **Components**: PostgreSQL Server, pgAdmin 4, Command Line Tools.  
  - **Port**: Keep default `5432`.  
  - **Password**: Set an admin password for the `postgres` user (remember this).  

### 3. Verify
Open **pgAdmin 4** (installed with PostgreSQL).  
- Register a new server:  
  - **Host**: `localhost`  
  - **Port**: `5432`  
  - **Username**: `postgres`  
  - **Password**: the one you set during install  

Create a database for alerts:
```sql
CREATE DATABASE alertsdb;
```

Create a user for the middleware:
```sql
CREATE USER alerts_user WITH PASSWORD 'alerts123';
GRANT ALL PRIVILEGES ON DATABASE alertsdb TO alerts_user;
```

Create the alerts table:
```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE wazuh_alerts (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP NOT NULL,
    event_type TEXT NOT NULL,
    source_module TEXT NOT NULL,
    payload JSONB NOT NULL
);

ALTER TABLE wazuh_alerts OWNER TO alerts_user;
```

---

## 📥 Download PostgreSQL JDBC Driver

Your Java program needs the PostgreSQL **JDBC driver** (the `.jar` file) to connect to PostgreSQL.

- Download from Maven Central:  
  👉 [PostgreSQL JDBC Driver (postgresql-42.7.3.jar)](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar)

- After download, place it inside your project folder under:  
  ```
  middleware-db/lib/postgresql-42.7.3.jar
  ```

Your folder should look like:
```
middleware-db/
├── lib/
│   └── postgresql-42.7.3.jar
├── src/
│   └── main/java/com/yourorg/middleware/...
├── out/
```

---

## ⚙️ Run the Middleware

### 1. Compile
Inside the project folder (`middleware-db`), run:
```bash
javac -cp lib\postgresql-42.7.3.jar -d out src\main\java\com\yourorg\middleware\*.java
```

### 2. Run
```bash
java -cp "out;lib\postgresql-42.7.3.jar" com.yourorg.middleware.DbDemo
```

Expected output:
```
✔ Alert inserted into DB
✔ Found 1 high severity alerts:
c07164d0-e24a-48f3-9c94-f8dd383c95c1 | {"host_id": "host-192.168.1.101", "severity": "high", "alert_type": "ransomware_detection"}
```

---

## 🔎 Query in pgAdmin

Open Query Tool in `alertsdb` and run:

Show all alerts:
```sql
SELECT * FROM wazuh_alerts;
```

Filter by severity:
```sql
SELECT event_id, payload
FROM wazuh_alerts
WHERE payload->>'severity' = 'high';
```

Filter by host:
```sql
SELECT *
FROM wazuh_alerts
WHERE payload->>'host_id' = 'host-192.168.1.101';
```
