-- Initial schema: USERS, PRODUCTS, ORDERS, ORDER_ITEMS, USER_ROLES + per-entity sequences.
-- Columns mirror @Column annotations on the entities exactly (lengths, nullability, uniqueness).
-- USERS is a reserved word in Oracle; the @Table(name = "USERS") on User.java works because
-- Hibernate auto-quotes it. We do the same with "USERS" in DDL.

--------------------------------------------------------------------------------
-- Sequences (allocationSize = 50 in JPA → INCREMENT BY 50 here so the cache lines up)
--------------------------------------------------------------------------------
CREATE SEQUENCE USER_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE;
CREATE SEQUENCE PRODUCT_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE;
CREATE SEQUENCE ORDER_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE;
CREATE SEQUENCE ORDER_ITEM_SEQ START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE;

--------------------------------------------------------------------------------
-- USERS (reserved word — keep double-quoted)
--------------------------------------------------------------------------------
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
);

--------------------------------------------------------------------------------
-- USER_ROLES (element collection on User.roles)
--------------------------------------------------------------------------------
CREATE TABLE USER_ROLES (
    USER_ID         NUMBER(19)      NOT NULL,
    ROLE            VARCHAR2(30)    NOT NULL,
    CONSTRAINT FK_USER_ROLES_USER FOREIGN KEY (USER_ID) REFERENCES "USERS" (ID)
);
CREATE INDEX IDX_USER_ROLES_USER ON USER_ROLES (USER_ID);

--------------------------------------------------------------------------------
-- PRODUCTS
--------------------------------------------------------------------------------
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
);
CREATE INDEX IDX_PRODUCT_CATEGORY ON PRODUCTS (CATEGORY);
CREATE INDEX IDX_PRODUCT_SKU ON PRODUCTS (SKU);

--------------------------------------------------------------------------------
-- ORDERS
--------------------------------------------------------------------------------
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
);
CREATE INDEX IDX_ORDER_USER ON ORDERS (USER_ID);
CREATE INDEX IDX_ORDER_STATUS ON ORDERS (ORDER_STATUS);
CREATE INDEX IDX_ORDER_CREATED_AT ON ORDERS (CREATED_AT);

--------------------------------------------------------------------------------
-- ORDER_ITEMS
--------------------------------------------------------------------------------
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
);
CREATE INDEX IDX_ORDER_ITEM_ORDER ON ORDER_ITEMS (ORDER_ID);
CREATE INDEX IDX_ORDER_ITEM_PRODUCT ON ORDER_ITEMS (PRODUCT_ID);
