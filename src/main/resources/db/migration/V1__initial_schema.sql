-- Initial schema: USERS, PRODUCTS, ORDERS, ORDER_ITEMS, USER_ROLES + per-entity sequences.
-- Idempotent: each CREATE is wrapped in PL/SQL that swallows ORA-00955 (name already used)
-- and logs a skip message via DBMS_OUTPUT.PUT_LINE. Tables/indexes/sequences that already
-- exist are left alone; missing ones get created.
--
-- All objects belong to the Flyway-configured schema (spring.flyway.schemas=apiservice in
-- application.yml). Tables stay unqualified here — Flyway sets CURRENT_SCHEMA for the session.

SET SERVEROUTPUT ON;

--------------------------------------------------------------------------------
-- Sequences (allocationSize = 50 in JPA → INCREMENT BY 50 here so the cache lines up)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE USER_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE';
  DBMS_OUTPUT.PUT_LINE('Created sequence USER_SEQ');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Sequence USER_SEQ already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE PRODUCT_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE';
  DBMS_OUTPUT.PUT_LINE('Created sequence PRODUCT_SEQ');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Sequence PRODUCT_SEQ already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE ORDER_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE';
  DBMS_OUTPUT.PUT_LINE('Created sequence ORDER_SEQ');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Sequence ORDER_SEQ already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE ORDER_ITEM_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE';
  DBMS_OUTPUT.PUT_LINE('Created sequence ORDER_ITEM_SEQ');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Sequence ORDER_ITEM_SEQ already exists, skipping');
    ELSE RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- USERS (reserved word — keep double-quoted)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE "USERS" (
        ID              NUMBER(19)      NOT NULL,
        FIRST_NAME      VARCHAR2(50)    NOT NULL,
        LAST_NAME       VARCHAR2(50)    NOT NULL,
        EMAIL           VARCHAR2(100)   NOT NULL,
        PASSWORD_HASH   VARCHAR2(100)   NOT NULL,
        PHONE_NUMBER    VARCHAR2(20)    NOT NULL,
        ADDRESS         VARCHAR2(255),
        CITY            VARCHAR2(50),
        STATE           VARCHAR2(50),
        ZIPCODE         VARCHAR2(20),
        IS_ACTIVE       NUMBER(1)       DEFAULT 1 NOT NULL,
        USER_TYPE       VARCHAR2(20)    DEFAULT 'CUSTOMER',
        CREATED_AT      TIMESTAMP(6)    NOT NULL,
        UPDATED_AT      TIMESTAMP(6),
        CREATED_BY      VARCHAR2(100),
        UPDATED_BY      VARCHAR2(100),
        CONSTRAINT PK_USERS PRIMARY KEY (ID),
        CONSTRAINT UK_USER_EMAIL UNIQUE (EMAIL)
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table "USERS"');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table "USERS" already exists, skipping');
    ELSE RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- USER_ROLES (element collection on User.roles)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE USER_ROLES (
        USER_ID         NUMBER(19)      NOT NULL,
        ROLE            VARCHAR2(30)    NOT NULL,
        CONSTRAINT FK_USER_ROLES_USER FOREIGN KEY (USER_ID) REFERENCES "USERS" (ID)
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table USER_ROLES');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table USER_ROLES already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_USER_ROLES_USER ON USER_ROLES (USER_ID)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_USER_ROLES_USER');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_USER_ROLES_USER already exists, skipping');
    ELSE RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- PRODUCTS
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE PRODUCTS (
        ID              NUMBER(19)      NOT NULL,
        VERSION         NUMBER(19),
        SKU             VARCHAR2(50)    NOT NULL,
        NAME            VARCHAR2(100)   NOT NULL,
        DESCRIPTION     VARCHAR2(500),
        PRICE           NUMBER(10, 2)   NOT NULL,
        STOCK_QUANTITY  NUMBER(19)      DEFAULT 0 NOT NULL,
        MIN_STOCK_LEVEL NUMBER(19)      DEFAULT 10,
        CATEGORY        VARCHAR2(50),
        IS_AVAILABLE    NUMBER(1)       DEFAULT 1 NOT NULL,
        SUPPLIER        VARCHAR2(100),
        CREATED_AT      TIMESTAMP(6)    NOT NULL,
        UPDATED_AT      TIMESTAMP(6),
        CREATED_BY      VARCHAR2(100),
        UPDATED_BY      VARCHAR2(100),
        CONSTRAINT PK_PRODUCTS PRIMARY KEY (ID),
        CONSTRAINT UK_PRODUCT_SKU UNIQUE (SKU)
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table PRODUCTS');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table PRODUCTS already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_PRODUCT_CATEGORY ON PRODUCTS (CATEGORY)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_PRODUCT_CATEGORY');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_PRODUCT_CATEGORY already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_PRODUCT_SKU ON PRODUCTS (SKU)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_PRODUCT_SKU');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_PRODUCT_SKU already exists, skipping');
    ELSE RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- ORDERS
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE ORDERS (
        ID                  NUMBER(19)      NOT NULL,
        VERSION             NUMBER(19),
        ORDER_NUMBER        VARCHAR2(50)    NOT NULL,
        USER_ID             NUMBER(19)      NOT NULL,
        ORDER_STATUS        VARCHAR2(20)    NOT NULL,
        TOTAL_AMOUNT        NUMBER(12, 2)   NOT NULL,
        SHIPPING_ADDRESS    VARCHAR2(255),
        NOTES               VARCHAR2(500),
        ESTIMATED_DELIVERY  TIMESTAMP(6),
        CREATED_AT          TIMESTAMP(6)    NOT NULL,
        UPDATED_AT          TIMESTAMP(6),
        CREATED_BY          VARCHAR2(100),
        UPDATED_BY          VARCHAR2(100),
        CONSTRAINT PK_ORDERS PRIMARY KEY (ID),
        CONSTRAINT UK_ORDER_NUMBER UNIQUE (ORDER_NUMBER),
        CONSTRAINT FK_ORDER_USER FOREIGN KEY (USER_ID) REFERENCES "USERS" (ID),
        CONSTRAINT CK_ORDER_STATUS CHECK (ORDER_STATUS IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'))
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table ORDERS');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table ORDERS already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_USER ON ORDERS (USER_ID)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_ORDER_USER');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_ORDER_USER already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_STATUS ON ORDERS (ORDER_STATUS)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_ORDER_STATUS');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_ORDER_STATUS already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_CREATED_AT ON ORDERS (CREATED_AT)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_ORDER_CREATED_AT');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_ORDER_CREATED_AT already exists, skipping');
    ELSE RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- ORDER_ITEMS
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE ORDER_ITEMS (
        ID              NUMBER(19)      NOT NULL,
        ORDER_ID        NUMBER(19)      NOT NULL,
        PRODUCT_ID      NUMBER(19)      NOT NULL,
        PRODUCT_NAME    VARCHAR2(100)   NOT NULL,
        PRODUCT_SKU     VARCHAR2(50)    NOT NULL,
        UNIT_PRICE      NUMBER(10, 2)   NOT NULL,
        QUANTITY        NUMBER(19)      NOT NULL,
        DISCOUNT        NUMBER(10, 2),
        NOTES           VARCHAR2(200),
        CREATED_AT      TIMESTAMP(6)    NOT NULL,
        UPDATED_AT      TIMESTAMP(6),
        CREATED_BY      VARCHAR2(100),
        UPDATED_BY      VARCHAR2(100),
        CONSTRAINT PK_ORDER_ITEMS PRIMARY KEY (ID),
        CONSTRAINT FK_ORDER_ITEM_ORDER FOREIGN KEY (ORDER_ID) REFERENCES ORDERS (ID),
        CONSTRAINT FK_ORDER_ITEM_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES PRODUCTS (ID)
    )
  ]';
  DBMS_OUTPUT.PUT_LINE('Created table ORDER_ITEMS');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Table ORDER_ITEMS already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_ITEM_ORDER ON ORDER_ITEMS (ORDER_ID)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_ORDER_ITEM_ORDER');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_ORDER_ITEM_ORDER already exists, skipping');
    ELSE RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX IDX_ORDER_ITEM_PRODUCT ON ORDER_ITEMS (PRODUCT_ID)';
  DBMS_OUTPUT.PUT_LINE('Created index IDX_ORDER_ITEM_PRODUCT');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN DBMS_OUTPUT.PUT_LINE('Index IDX_ORDER_ITEM_PRODUCT already exists, skipping');
    ELSE RAISE; END IF;
END;
/
