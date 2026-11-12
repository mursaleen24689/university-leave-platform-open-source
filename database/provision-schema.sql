-- Run once, as a database administrator, connected to the FREEPDB1
-- pluggable database (not to CDB$ROOT).
--
-- Replace <strong-password> before running and never commit the real value.
-- The schema owner receives exactly what Flyway needs, and nothing more: no
-- DBA role, no CREATE ANY TABLE, so it cannot touch another schema on the
-- same instance.
--
-- After this runs, start the application. Flyway creates every table,
-- sequence and constraint in the LEAVEAPP schema.

CREATE USER LEAVEAPP IDENTIFIED BY "<strong-password>"
  DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW,
      CREATE PROCEDURE, CREATE TRIGGER, CREATE TYPE TO LEAVEAPP;

-- Verify the schema exists and is open.
SELECT username, account_status, default_tablespace
  FROM dba_users
 WHERE username = 'LEAVEAPP';
