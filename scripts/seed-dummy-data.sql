-- ============================================================================
-- Dummy data for local development and testing.
--
-- Idempotent: every INSERT is preceded by an existence check. If the row is
-- already there (matched on natural key — SKU, EMAIL, or ORDER_NUMBER), the
-- script logs a skip message and moves on. Safe to re-run.
--
-- Switches CURRENT_SCHEMA to apiservice so unqualified table references resolve
-- correctly when running as system or any other user with apiservice access.
--
-- Run this AFTER the schema has been created (Flyway via mvn spring-boot:run,
-- or scripts/apply-schema-manually.sql).
--
-- Seeds:
--   * 10 products across 4 categories
--   * 2 users (admin + customer), passwords are BCrypt-hashed (verified)
--   * Roles for each user
--   * 1 sample order from the customer with 3 line items
--
-- DEFAULT CREDENTIALS (created by this script):
--   admin@example.com    / Password123!     (roles: ADMIN, USER)
--   customer@example.com / Password123!     (roles: USER)
--
-- If you need to regenerate the BCrypt hash (e.g. you changed BCrypt strength):
--   python3 -c "import bcrypt; print(bcrypt.hashpw(b'Password123!', bcrypt.gensalt(rounds=12)).decode())"
-- then replace the PASSWORD_HASH literal in the two upsert_user calls below.
--
-- Usage:
--   sqlplus system/NewPassword@localhost:1521/APIServiceMONO @scripts/seed-dummy-data.sql
-- ============================================================================

SET SERVEROUTPUT ON;
WHENEVER SQLERROR EXIT FAILURE;

ALTER SESSION SET CURRENT_SCHEMA = apiservice;

PROMPT ===> Seeding products (idempotent on SKU)
--------------------------------------------------------------------------------
DECLARE
  PROCEDURE upsert_product(
      p_sku        VARCHAR2,
      p_name       VARCHAR2,
      p_desc       VARCHAR2,
      p_price      NUMBER,
      p_stock      NUMBER,
      p_min_stock  NUMBER,
      p_category   VARCHAR2,
      p_supplier   VARCHAR2
  ) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM PRODUCTS WHERE SKU = p_sku;
    IF v_count = 0 THEN
      INSERT INTO PRODUCTS (
          ID, VERSION, SKU, NAME, DESCRIPTION, PRICE, STOCK_QUANTITY,
          MIN_STOCK_LEVEL, CATEGORY, IS_AVAILABLE, SUPPLIER,
          CREATED_AT, CREATED_BY
      ) VALUES (
          PRODUCT_SEQ.NEXTVAL, 0, p_sku, p_name, p_desc, p_price, p_stock,
          p_min_stock, p_category, 1, p_supplier,
          SYSTIMESTAMP, 'system'
      );
      DBMS_OUTPUT.PUT_LINE('Inserted product ' || p_sku);
    ELSE
      DBMS_OUTPUT.PUT_LINE('Product ' || p_sku || ' already exists, skipping');
    END IF;
  END;
BEGIN
  upsert_product('KBD-001',   'Mechanical Keyboard',         'Tactile switches, hot-swappable, USB-C',     129.99, 50, 10, 'PERIPHERALS', 'Keystone');
  upsert_product('MOU-001',   'Wireless Mouse',              'Low-latency 2.4GHz, USB-C charging',          59.99, 80, 15, 'PERIPHERALS', 'Keystone');
  upsert_product('HEAD-001',  'Noise-cancelling Headphones', '40h battery, ANC, Bluetooth 5.3',            249.00, 30, 10, 'PERIPHERALS', 'Auralink');
  upsert_product('MON-001',   '27" 4K Monitor',              'IPS panel, 60Hz, HDR10',                     349.00, 20,  5, 'DISPLAYS',    'Pixelworks');
  upsert_product('MON-002',   '32" Curved Ultrawide',        '34:9, 144Hz, USB-C 90W PD',                  699.00, 12,  5, 'DISPLAYS',    'Pixelworks');
  upsert_product('HUB-001',   'USB-C Hub',                   '7-in-1: HDMI 4K60, 100W PD, SD/microSD',      39.50, 100, 25, 'ACCESSORIES', 'NodeOne');
  upsert_product('STD-001',   'Laptop Stand',                'Aluminum, adjustable height + tilt',          49.00, 60, 15, 'ACCESSORIES', 'NodeOne');
  upsert_product('CHAIR-001', 'Ergonomic Office Chair',      'Mesh back, lumbar support, 4D arms',         459.00,  8,  3, 'ACCESSORIES', 'Postura');
  upsert_product('NET-001',   'Wi-Fi 6E Router',             'Tri-band, 6GHz, mesh-capable',               199.00, 25, 10, 'NETWORKING',  'MeshLogic');
  upsert_product('NET-002',   'Network Switch 8-port',       'Managed gigabit, PoE+',                      159.00, 18,  5, 'NETWORKING',  'MeshLogic');
END;
/

PROMPT ===> Seeding users + roles (idempotent on EMAIL)
--------------------------------------------------------------------------------
DECLARE
  PROCEDURE upsert_user(
      p_email      VARCHAR2,
      p_first      VARCHAR2,
      p_last       VARCHAR2,
      p_phone      VARCHAR2,
      p_address    VARCHAR2,
      p_city       VARCHAR2,
      p_state      VARCHAR2,
      p_zip        VARCHAR2,
      p_user_type  VARCHAR2,
      p_roles      SYS.ODCIVARCHAR2LIST  -- one or more roles
  ) IS
    v_count    NUMBER;
    v_user_id  NUMBER;
    v_role     VARCHAR2(30);
  BEGIN
    SELECT COUNT(*) INTO v_count FROM "USERS" WHERE EMAIL = p_email;
    IF v_count = 0 THEN
      INSERT INTO "USERS" (
          ID, FIRST_NAME, LAST_NAME, EMAIL, PASSWORD_HASH, PHONE_NUMBER,
          ADDRESS, CITY, STATE, ZIPCODE, IS_ACTIVE, USER_TYPE,
          CREATED_AT, CREATED_BY
      ) VALUES (
          USER_SEQ.NEXTVAL, p_first, p_last, p_email,
          '$2a$12$xQwKVx/kcbIamg94dIuzHuUfH1BAS2vPOYyzOudWH3touEO0HSB82',
          p_phone, p_address, p_city, p_state, p_zip,
          1, p_user_type, SYSTIMESTAMP, 'system'
      ) RETURNING ID INTO v_user_id;

      FOR i IN 1 .. p_roles.COUNT LOOP
        v_role := p_roles(i);
        INSERT INTO USER_ROLES (USER_ID, ROLE) VALUES (v_user_id, v_role);
      END LOOP;

      DBMS_OUTPUT.PUT_LINE('Inserted user ' || p_email || ' with ' || p_roles.COUNT || ' role(s)');
    ELSE
      DBMS_OUTPUT.PUT_LINE('User ' || p_email || ' already exists, skipping');
    END IF;
  END;
BEGIN
  upsert_user(
      'admin@example.com', 'Admin', 'User',
      '5550000001', '1 Operations Way', 'San Francisco', 'CA', '94103',
      'ADMIN',
      SYS.ODCIVARCHAR2LIST('ADMIN', 'USER')
  );
  upsert_user(
      'customer@example.com', 'Alice', 'Customer',
      '5550000002', '42 Pleasant St', 'Brooklyn', 'NY', '11201',
      'CUSTOMER',
      SYS.ODCIVARCHAR2LIST('USER')
  );
END;
/

PROMPT ===> Seeding sample order for customer (idempotent on ORDER_NUMBER)
--------------------------------------------------------------------------------
DECLARE
  v_existing      NUMBER;
  v_customer_id   NUMBER;
  v_order_id      NUMBER;
  v_kbd_id        NUMBER;  v_kbd_price   NUMBER;
  v_mouse_id      NUMBER;  v_mouse_price NUMBER;
  v_hub_id        NUMBER;  v_hub_price   NUMBER;
  v_total         NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_existing FROM ORDERS WHERE ORDER_NUMBER = 'ORD-SEED-0001';
  IF v_existing > 0 THEN
    DBMS_OUTPUT.PUT_LINE('Order ORD-SEED-0001 already exists, skipping');
    RETURN;
  END IF;

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

  DBMS_OUTPUT.PUT_LINE('Inserted order ORD-SEED-0001 (3 items, total=' || v_total || ')');
END;
/

COMMIT;

PROMPT
PROMPT ============================================================================
PROMPT Seed data step complete (inserts ran where missing, skipped where present).
PROMPT
PROMPT Default password for BOTH users:   Password123!
PROMPT
PROMPT Try logging in:
PROMPT   curl -X POST http://localhost:8080/api/v1/auth/login \
PROMPT     -H 'Content-Type: application/json' \
PROMPT     -d '{"email":"customer@example.com","password":"Password123!"}'
PROMPT ============================================================================
