-- ============================================================================
-- Dummy data for local development and testing.
--
-- Run this AFTER the schema has been created (Flyway via mvn spring-boot:run,
-- or scripts/apply-schema-manually.sql).
--
-- Seeds:
--   * 10 products across 4 categories
--   * 2 users (admin + customer), passwords are BCrypt-hashed
--   * Roles for each user
--   * 1 sample order from the customer with 3 line items
--
-- DEFAULT CREDENTIALS (created by this script):
--   admin@example.com    / Password123!     (roles: ADMIN, USER)
--   customer@example.com / Password123!     (roles: USER)
--
-- If the embedded BCrypt hash doesn't verify in your app (e.g., login returns
-- 401 even with the correct password), regenerate with this jshell one-liner:
--
--   jshell -s
--   /env -class-path $HOME/.m2/repository/org/springframework/security/spring-security-crypto/6.3.4/spring-security-crypto-6.3.4.jar
--   System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Password123!"));
--
-- Then replace the PASSWORD_HASH literal in the two INSERT statements below.
--
-- Usage:
--   sqlplus system/password@localhost:1521/FREEPDB1 @scripts/seed-dummy-data.sql
--
-- Safe to re-run? NO — the UNIQUE constraints on USERS.EMAIL, PRODUCTS.SKU,
-- and ORDERS.ORDER_NUMBER will throw ORA-00001 on second execution. Clean
-- first with `DELETE FROM ...` if you want to re-seed.
-- ============================================================================

SET ECHO ON
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

PROMPT ===> Seeding products
--------------------------------------------------------------------------------
-- 10 products across PERIPHERALS, DISPLAYS, ACCESSORIES, NETWORKING
--------------------------------------------------------------------------------
INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'KBD-001', 'Mechanical Keyboard', 'Tactile switches, hot-swappable, USB-C', 129.99, 50, 10, 'PERIPHERALS', 1, 'Keystone', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'MOU-001', 'Wireless Mouse', 'Low-latency 2.4GHz, USB-C charging', 59.99, 80, 15, 'PERIPHERALS', 1, 'Keystone', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'HEAD-001', 'Noise-cancelling Headphones', '40h battery, ANC, Bluetooth 5.3', 249.00, 30, 10, 'PERIPHERALS', 1, 'Auralink', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'MON-001', '27" 4K Monitor', 'IPS panel, 60Hz, HDR10', 349.00, 20, 5, 'DISPLAYS', 1, 'Pixelworks', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'MON-002', '32" Curved Ultrawide', '34:9, 144Hz, USB-C 90W PD', 699.00, 12, 5, 'DISPLAYS', 1, 'Pixelworks', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'HUB-001', 'USB-C Hub', '7-in-1: HDMI 4K60, 100W PD, SD/microSD, 3xUSB-A', 39.50, 100, 25, 'ACCESSORIES', 1, 'NodeOne', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'STD-001', 'Laptop Stand', 'Aluminum, adjustable height + tilt', 49.00, 60, 15, 'ACCESSORIES', 1, 'NodeOne', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'CHAIR-001', 'Ergonomic Office Chair', 'Mesh back, lumbar support, 4D arms', 459.00, 8, 3, 'ACCESSORIES', 1, 'Postura', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'NET-001', 'Wi-Fi 6E Router', 'Tri-band, 6GHz, mesh-capable', 199.00, 25, 10, 'NETWORKING', 1, 'MeshLogic', SYSTIMESTAMP, 'system');

INSERT INTO PRODUCTS (ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY, MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER, CREATED_AT, CREATED_BY)
VALUES (PRODUCT_SEQ.NEXTVAL, 0, 'NET-002', 'Network Switch 8-port', 'Managed gigabit, PoE+', 159.00, 18, 5, 'NETWORKING', 1, 'MeshLogic', SYSTIMESTAMP, 'system');

PROMPT ===> Seeding users + roles
--------------------------------------------------------------------------------
-- Admin user (Password123!) — BCrypt strength 12 hash
--------------------------------------------------------------------------------
DECLARE
    v_user_id NUMBER;
BEGIN
    INSERT INTO "USERS" (
        ID, FIRST_NAME, LAST_NAME, EMAIL, PASSWORD_HASH, PHONE_NUMBER,
        ADDRESS, CITY, STATE, ZIPCODE, IS_ACTIVE, USER_TYPE, CREATED_AT, CREATED_BY
    ) VALUES (
        USER_SEQ.NEXTVAL, 'Admin', 'User', 'admin@example.com',
        '$2a$12$xQwKVx/kcbIamg94dIuzHuUfH1BAS2vPOYyzOudWH3touEO0HSB82',
        '5550000001',
        '1 Operations Way', 'San Francisco', 'CA', '94103',
        1, 'ADMIN', SYSTIMESTAMP, 'system'
    ) RETURNING ID INTO v_user_id;

    INSERT INTO USER_ROLES (USER_ID, ROLE) VALUES (v_user_id, 'ADMIN');
    INSERT INTO USER_ROLES (USER_ID, ROLE) VALUES (v_user_id, 'USER');
END;
/

--------------------------------------------------------------------------------
-- Customer user (Password123!)
--------------------------------------------------------------------------------
DECLARE
    v_user_id NUMBER;
BEGIN
    INSERT INTO "USERS" (
        ID, FIRST_NAME, LAST_NAME, EMAIL, PASSWORD_HASH, PHONE_NUMBER,
        ADDRESS, CITY, STATE, ZIPCODE, IS_ACTIVE, USER_TYPE, CREATED_AT, CREATED_BY
    ) VALUES (
        USER_SEQ.NEXTVAL, 'Alice', 'Customer', 'customer@example.com',
        '$2a$12$xQwKVx/kcbIamg94dIuzHuUfH1BAS2vPOYyzOudWH3touEO0HSB82',
        '5550000002',
        '42 Pleasant St', 'Brooklyn', 'NY', '11201',
        1, 'CUSTOMER', SYSTIMESTAMP, 'system'
    ) RETURNING ID INTO v_user_id;

    INSERT INTO USER_ROLES (USER_ID, ROLE) VALUES (v_user_id, 'USER');
END;
/

PROMPT ===> Seeding sample order for customer
--------------------------------------------------------------------------------
-- One order from the customer with 3 line items (keyboard + mouse + hub)
-- Status = DELIVERED so it shows up in the "My Orders" page with a final state.
-- Stock for those products has NOT been decremented — for SQL-seeded data
-- the bookkeeping is simpler if we leave stock as-is. To realistically simulate
-- stock effects, create orders via the API (POST /api/v1/orders) instead.
--------------------------------------------------------------------------------
DECLARE
    v_customer_id NUMBER;
    v_order_id    NUMBER;
    v_kbd_id      NUMBER;  v_kbd_price   NUMBER;
    v_mouse_id    NUMBER;  v_mouse_price NUMBER;
    v_hub_id      NUMBER;  v_hub_price   NUMBER;
    v_total       NUMBER;
BEGIN
    SELECT ID INTO v_customer_id FROM "USERS" WHERE EMAIL = 'customer@example.com';

    SELECT ID, PRICE INTO v_kbd_id,   v_kbd_price   FROM PRODUCTS WHERE SKU = 'KBD-001';
    SELECT ID, PRICE INTO v_mouse_id, v_mouse_price FROM PRODUCTS WHERE SKU = 'MOU-001';
    SELECT ID, PRICE INTO v_hub_id,   v_hub_price   FROM PRODUCTS WHERE SKU = 'HUB-001';

    v_total := v_kbd_price + (2 * v_mouse_price) + v_hub_price;

    INSERT INTO ORDERS (
        ID, VERSION, ORDER_NUMBER, USER_ID, ORDER_STATUS, TOTAL_AMOUNT,
        SHIPPING_ADDRESS, NOTES, ESTIMATED_DELIVERY, CREATED_AT, CREATED_BY
    ) VALUES (
        ORDER_SEQ.NEXTVAL, 0, 'ORD-SEED-0001', v_customer_id, 'DELIVERED', v_total,
        '42 Pleasant St, Brooklyn, NY 11201', 'Sample seeded order',
        SYSTIMESTAMP - INTERVAL '5' DAY, SYSTIMESTAMP - INTERVAL '12' DAY, 'system'
    ) RETURNING ID INTO v_order_id;

    INSERT INTO ORDER_ITEMS (ID, ORDER_ID, PRODUCT_ID, PRODUCT_NAME, PRODUCT_SKU, UNIT_PRICE, QUANTITY, DISCOUNT, CREATED_AT, CREATED_BY)
    VALUES (ORDER_ITEM_SEQ.NEXTVAL, v_order_id, v_kbd_id,   'Mechanical Keyboard', 'KBD-001', v_kbd_price,   1, 0, SYSTIMESTAMP - INTERVAL '12' DAY, 'system');

    INSERT INTO ORDER_ITEMS (ID, ORDER_ID, PRODUCT_ID, PRODUCT_NAME, PRODUCT_SKU, UNIT_PRICE, QUANTITY, DISCOUNT, CREATED_AT, CREATED_BY)
    VALUES (ORDER_ITEM_SEQ.NEXTVAL, v_order_id, v_mouse_id, 'Wireless Mouse',      'MOU-001', v_mouse_price, 2, 0, SYSTIMESTAMP - INTERVAL '12' DAY, 'system');

    INSERT INTO ORDER_ITEMS (ID, ORDER_ID, PRODUCT_ID, PRODUCT_NAME, PRODUCT_SKU, UNIT_PRICE, QUANTITY, DISCOUNT, CREATED_AT, CREATED_BY)
    VALUES (ORDER_ITEM_SEQ.NEXTVAL, v_order_id, v_hub_id,   'USB-C Hub',           'HUB-001', v_hub_price,   1, 0, SYSTIMESTAMP - INTERVAL '12' DAY, 'system');
END;
/

COMMIT;

PROMPT
PROMPT ============================================================================
PROMPT Seed data inserted successfully.
PROMPT
PROMPT   Products:        10 rows
PROMPT   Users:           2 (admin@example.com, customer@example.com)
PROMPT   Roles:           3 mappings (admin has ADMIN+USER, customer has USER)
PROMPT   Sample order:    ORD-SEED-0001 (DELIVERED, 3 items)
PROMPT
PROMPT Default password for BOTH users:   Password123!
PROMPT
PROMPT Try logging in:
PROMPT   curl -X POST http://localhost:8080/api/v1/auth/login \
PROMPT     -H 'Content-Type: application/json' \
PROMPT     -d '{"email":"customer@example.com","password":"Password123!"}'
PROMPT ============================================================================
