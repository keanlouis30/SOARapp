# PostgreSQL Setup Guide

## Why PostgreSQL?
- Open-source and free  
- Reliable, widely used in industry  
- Strong support for JSON/UUID fields (good for storing alerts)  
- Compatible with Java via JDBC  

---

## Requirements
- **Java 17+** installed  
- **PostgreSQL 17** installed  
- **PostgreSQL JDBC Driver** (`postgresql-42.7.7.jar`)  

Download JDBC driver:  
👉 https://jdbc.postgresql.org/download.html  
Place it inside the `lib/` folder of this project.  

---

## Step 1: Install PostgreSQL
1. Download installer:  
   👉 [EnterpriseDB PostgreSQL Installer](https://www.enterprisedb.com/downloads/postgres-postgresql-downloads)
2. During installation:  
   - Choose **PostgreSQL Server**  
   - Port: `5432` (default)  
   - Superuser: `postgres`  
   - Password: (choose your password, e.g. `postgres`)  
3. Once installed, open **pgAdmin 4** or `psql`.  

---

## Step 2: Create Database and User
1. Open **pgAdmin 4** and connect with:  
   - Host: `localhost`  
   - User: `postgres`  
   - Password: `your_password`  
2. Run the following SQL to create the database and user:

```sql
-- Create database
CREATE DATABASE alertsdb;

-- Connect to database
\c alertsdb;

-- Create alerts user
CREATE USER alerts_user WITH PASSWORD 'alertspass';

-- Create alerts table
CREATE TABLE wazuh_alerts (
    event_id UUID PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    event_type TEXT NOT NULL,
    source_module TEXT NOT NULL,
    payload JSONB NOT NULL
);

-- Give permissions
GRANT ALL PRIVILEGES ON DATABASE alertsdb TO alerts_user;
GRANT ALL PRIVILEGES ON TABLE wazuh_alerts TO alerts_user;
```

---

## Step 3: Compile Middleware
Inside project folder (`middleware-sender/`):

```bash
javac -cp lib/postgresql-42.7.7.jar -d out src/main/java/com/yourorg/middleware/*.java
```

---

## Step 4: Run Sender (Insert Alerts)

```bash
java -cp "out;lib/postgresql-42.7.7.jar" com.yourorg.middleware.Sender
```

This will send 10 hardcoded alerts into PostgreSQL.

---

## Step 5: Run QueryDemo (Search Alerts)

```bash
java -cp "out;lib/postgresql-42.7.7.jar" com.yourorg.middleware.QueryDemo
```

This will print alerts with `severity = high`.

---

## Example Output

```
🔍 Alerts with severity = high
ID: c07164d0-e24a-48f3-9c94-f8dd383c95c1
Type: alerts.host.wazuh
Source: WazuhConnector
Payload: {"host_id": "host-192.168.1.101", "severity": "high", "alert_type": "ransomware_detection"}
--------------------------------------------------
ID: 1d57980a-7a1a-47f6-9efd-cd87a51dffdd
Type: alerts.host.wazuh
Source: WazuhConnector
Payload: {"host_id": "host-192.168.1.101", "severity": "high", "alert_type": "ransomware_detection"}
--------------------------------------------------
```
