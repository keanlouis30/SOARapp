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
