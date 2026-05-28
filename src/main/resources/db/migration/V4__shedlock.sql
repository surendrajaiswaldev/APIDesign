-- ShedLock distributed-lock table. Required by JdbcTemplateLockProvider.
-- One row per @SchedulerLock(name=...) — auto-inserted on first acquisition.

CREATE TABLE SHEDLOCK (
    NAME        VARCHAR2(64)    NOT NULL,
    LOCK_UNTIL  TIMESTAMP(3)    NOT NULL,
    LOCKED_AT   TIMESTAMP(3)    NOT NULL,
    LOCKED_BY   VARCHAR2(255)   NOT NULL,
    CONSTRAINT SHEDLOCK_PK PRIMARY KEY (NAME)
);
