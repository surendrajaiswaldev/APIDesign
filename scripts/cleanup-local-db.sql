-- ============================================================================
-- One-time local DB cleanup for the Flyway adoption.
--
-- Run this against your local Oracle BEFORE the first `mvn spring-boot:run`
-- after pulling the Flyway changes. It wipes any tables/sequences left from
-- the old `ddl-auto: create-drop` runs so Flyway can rebuild from V1.
--
-- Safe to run on an empty schema — the ORA-00942 / ORA-02289 errors that
-- appear for already-missing objects are expected and harmless.
--
-- Usage:
--   sqlplus system/password@localhost:1521/FREEPDB1 @scripts/cleanup-local-db.sql
--
-- Or from inside sqlplus:
--   SQL> @scripts/cleanup-local-db.sql
-- ============================================================================

SET ECHO ON
SET SERVEROUTPUT ON

-- Tables (in FK-safe order; CASCADE CONSTRAINTS handles the rest)
DROP TABLE ORDER_ITEMS           CASCADE CONSTRAINTS;
DROP TABLE ORDERS                CASCADE CONSTRAINTS;
DROP TABLE USER_ROLES            CASCADE CONSTRAINTS;
DROP TABLE REFRESH_TOKENS        CASCADE CONSTRAINTS;
DROP TABLE PRODUCTS              CASCADE CONSTRAINTS;
DROP TABLE "USERS"               CASCADE CONSTRAINTS;
DROP TABLE APPLICATION_EXCEPTION CASCADE CONSTRAINTS;
DROP TABLE SHEDLOCK              CASCADE CONSTRAINTS;

-- Flyway's own history table — drop this so V1..V4 re-run from scratch
DROP TABLE FLYWAY_SCHEMA_HISTORY CASCADE CONSTRAINTS;

-- Sequences (one per entity, allocationSize=50)
DROP SEQUENCE USER_SEQ;
DROP SEQUENCE PRODUCT_SEQ;
DROP SEQUENCE ORDER_SEQ;
DROP SEQUENCE ORDER_ITEM_SEQ;
DROP SEQUENCE APP_EXCEPTION_SEQ;
DROP SEQUENCE REFRESH_TOKEN_SEQ;

PROMPT
PROMPT Cleanup complete. Any ORA-00942 (table missing) or ORA-02289
PROMPT (sequence missing) errors above are safe to ignore.
PROMPT
PROMPT Now run: mvn spring-boot:run
PROMPT
