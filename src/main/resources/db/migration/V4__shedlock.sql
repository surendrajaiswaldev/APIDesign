-- ShedLock distributed-lock table. Required by JdbcTemplateLockProvider.
-- One row per @SchedulerLock(name=...) — auto-inserted on first acquisition.
-- Idempotent: ORA-00955 (name already used) is logged and skipped.

SET SERVEROUTPUT ON;

BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE SHEDLOCK (
        NAME        VARCHAR2(64)    NOT NULL,
        LOCK_UNTIL  TIMESTAMP(3)    NOT NULL,
        LOCKED_AT   TIMESTAMP(3)    NOT NULL,
        LOCKED_BY   VARCHAR2(255)   NOT NULL,
        CONSTRAINT SHEDLOCK_PK PRIMARY KEY (NAME)
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table SHEDLOCK');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table SHEDLOCK already exists, skipping');
    ELSE RAISE; END IF;
END;
/
