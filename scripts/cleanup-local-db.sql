-- ============================================================================
-- One-time local DB cleanup for the apiservice schema.
--
-- Drops every Flyway-managed table + sequence inside the apiservice schema.
-- All references use the fully-qualified "<schema>"."<table>" format so the
-- script works regardless of the CURRENT_SCHEMA of the connecting user.
--
-- Each DROP is wrapped in PL/SQL that checks existence first (via all_tables /
-- all_sequences) and logs whether it ran or skipped — no silent ORA-00942 /
-- ORA-02289 noise, no unexpected failures on partial state.
--
-- Note on schema casing: Oracle stores unquoted identifiers in upper case.
-- The Flyway config writes `schemas: apiservice` (lower-case in YAML); Oracle
-- folds that to APISERVICE in the data dictionary, so we look up by the
-- uppercase name. The quoted "apiservice" in DROP statements works because
-- Oracle accepts both forms in DDL even when the stored name is uppercase
-- (the wrapper queries are case-insensitive via UPPER).
--
-- Usage:
--   sqlplus system/NewPassword@localhost:1521/APIServiceMONO @scripts/cleanup-local-db.sql
--
-- Or from inside sqlplus:
--   SQL> @scripts/cleanup-local-db.sql
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- Tables (FK-safe order; CASCADE CONSTRAINTS handles the rest)
-- ----------------------------------------------------------------------------
DECLARE
  PROCEDURE drop_table_if_exists(p_table IN VARCHAR2) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count
      FROM all_tables
     WHERE owner = UPPER('apiservice')
       AND table_name = UPPER(p_table);
    IF v_count > 0 THEN
      EXECUTE IMMEDIATE 'DROP TABLE "apiservice"."' || p_table || '" CASCADE CONSTRAINTS';
      DBMS_OUTPUT.PUT_LINE('Dropped "apiservice"."' || p_table || '"');
    ELSE
      DBMS_OUTPUT.PUT_LINE('Table "apiservice"."' || p_table || '" does not exist, skipping');
    END IF;
  END;
BEGIN
  drop_table_if_exists('ORDER_ITEMS');
  drop_table_if_exists('ORDERS');
  drop_table_if_exists('USER_ROLES');
  drop_table_if_exists('REFRESH_TOKENS');
  drop_table_if_exists('PRODUCTS');
  drop_table_if_exists('USERS');
  drop_table_if_exists('APPLICATION_EXCEPTION');
  drop_table_if_exists('SHEDLOCK');
  drop_table_if_exists('flyway_schema_history');
END;
/

-- ----------------------------------------------------------------------------
-- Sequences (one per entity, allocationSize=50)
-- ----------------------------------------------------------------------------
DECLARE
  PROCEDURE drop_sequence_if_exists(p_seq IN VARCHAR2) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count
      FROM all_sequences
     WHERE sequence_owner = UPPER('apiservice')
       AND sequence_name = UPPER(p_seq);
    IF v_count > 0 THEN
      EXECUTE IMMEDIATE 'DROP SEQUENCE "apiservice"."' || p_seq || '"';
      DBMS_OUTPUT.PUT_LINE('Dropped sequence "apiservice"."' || p_seq || '"');
    ELSE
      DBMS_OUTPUT.PUT_LINE('Sequence "apiservice"."' || p_seq || '" does not exist, skipping');
    END IF;
  END;
BEGIN
  drop_sequence_if_exists('USER_SEQ');
  drop_sequence_if_exists('PRODUCT_SEQ');
  drop_sequence_if_exists('ORDER_SEQ');
  drop_sequence_if_exists('ORDER_ITEM_SEQ');
  drop_sequence_if_exists('APP_EXCEPTION_SEQ');
  drop_sequence_if_exists('REFRESH_TOKEN_SEQ');
END;
/

PROMPT
PROMPT Cleanup complete. Run `mvn spring-boot:run` to let Flyway recreate
PROMPT the schema from V1..V4.
PROMPT
