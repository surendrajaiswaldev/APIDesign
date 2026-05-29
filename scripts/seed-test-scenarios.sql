-- ============================================================================
-- Rich test-scenario seed data for exercising every API endpoint.
--
-- Layered ON TOP of scripts/seed-dummy-data.sql (which seeds the baseline
-- admin + customer + 10 products + 1 sample order). Run this after the
-- baseline to expand the dataset for realistic API testing.
--
-- Idempotent: every INSERT is guarded by an EXISTS check; safe to re-run.
--
-- WHAT THIS SEEDS:
--   8 additional users covering edge cases:
--     • inactive@example.com    — IS_ACTIVE=0; login should be rejected
--     • poweruser@example.com   — has 6 orders covering all order statuses
--     • newuser@example.com     — no orders; profile only partially filled
--     • vip@example.com         — second ADMIN account
--     • shopper1..4@example.com — generic users for pagination tests
--
--   10 additional products covering edge cases:
--     • out-of-stock (stock = 0)
--     • low-stock (stock < min_stock_level — surfaces in /products/lowstock)
--     • unavailable (IS_AVAILABLE = 0)
--     • cheap ($9.99) and premium ($1999.00) for price-range search
--     • Unicode name for i18n test
--
--   6 additional orders covering EVERY OrderStatus:
--     • 1 PENDING (recent)
--     • 1 PENDING-stale (created 25 h ago — eligible for PendingOrderCleanupJob)
--     • 1 CONFIRMED
--     • 1 SHIPPED
--     • 1 DELIVERED (in addition to the baseline ORD-SEED-0001)
--     • 1 CANCELLED (with saga in COMPENSATED state for poweruser)
--     Plus extra DELIVERED orders for poweruser so /orders/user/{id} returns
--     enough rows to exercise pagination.
--
--   2 saga rows demonstrating success + compensation states.
--
-- All test users share password 'Password123!' (same BCrypt hash as baseline).
--
-- Usage:
--   sqlplus system/NewPassword@localhost:1521/APIServiceMONO @scripts/seed-test-scenarios.sql
-- ============================================================================

SET SERVEROUTPUT ON;
WHENEVER SQLERROR EXIT FAILURE;

ALTER SESSION SET CURRENT_SCHEMA = apiservice;

PROMPT ===> Seeding extra users (idempotent on EMAIL)
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
      p_active     NUMBER,
      p_roles      SYS.ODCIVARCHAR2LIST
  ) IS
    v_count   NUMBER;
    v_user_id NUMBER;
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
          p_active, p_user_type, SYSTIMESTAMP, 'system'
      ) RETURNING ID INTO v_user_id;

      FOR i IN 1 .. p_roles.COUNT LOOP
        INSERT INTO USER_ROLES (USER_ID, ROLE) VALUES (v_user_id, p_roles(i));
      END LOOP;

      DBMS_OUTPUT.PUT_LINE('Inserted user ' || p_email || ' (active=' || p_active || ')');
    ELSE
      DBMS_OUTPUT.PUT_LINE('User ' || p_email || ' already exists, skipping');
    END IF;
  END;
BEGIN
  -- Inactive user — exercises login-rejection path
  upsert_user('inactive@example.com', 'Inactive', 'Account',
      '5550001000', '1 Closed Lane', 'Phoenix', 'AZ', '85001',
      'CUSTOMER', 0, SYS.ODCIVARCHAR2LIST('USER'));

  -- Power buyer — gets 6 orders for pagination + status-filter tests
  upsert_user('poweruser@example.com', 'Pat', 'Power',
      '5550002000', '99 Cart Avenue', 'Austin', 'TX', '78701',
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));

  -- New user — empty profile, no orders. Tests "profile partial" rendering
  upsert_user('newuser@example.com', 'Newbie', 'Joiner',
      '5550003000', NULL, NULL, NULL, NULL,
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));

  -- Second ADMIN
  upsert_user('vip@example.com', 'Vera', 'VIP',
      '5550004000', '1 Executive Way', 'Seattle', 'WA', '98101',
      'ADMIN', 1, SYS.ODCIVARCHAR2LIST('ADMIN', 'USER'));

  -- Generic shoppers for /users pagination
  upsert_user('shopper1@example.com', 'Sam', 'One',
      '5550005001', '10 Main St', 'Chicago', 'IL', '60601',
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));
  upsert_user('shopper2@example.com', 'Sloane', 'Two',
      '5550005002', '20 Oak Rd', 'Denver', 'CO', '80201',
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));
  upsert_user('shopper3@example.com', 'Sky', 'Three',
      '5550005003', '30 Pine Way', 'Portland', 'OR', '97201',
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));
  upsert_user('shopper4@example.com', 'Sage', 'Four',
      '5550005004', '40 Cedar Ln', 'Boston', 'MA', '02108',
      'CUSTOMER', 1, SYS.ODCIVARCHAR2LIST('USER'));
END;
/

PROMPT ===> Seeding extra products (idempotent on SKU)
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
      p_supplier   VARCHAR2,
      p_available  NUMBER
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
          p_min_stock, p_category, p_available, p_supplier,
          SYSTIMESTAMP, 'system'
      );
      DBMS_OUTPUT.PUT_LINE('Inserted product ' || p_sku
          || ' (stock=' || p_stock || ', available=' || p_available || ')');
    ELSE
      DBMS_OUTPUT.PUT_LINE('Product ' || p_sku || ' already exists, skipping');
    END IF;
  END;
BEGIN
  -- Out of stock — exercise InsufficientStock path on order creation
  upsert_product('KBD-002', 'Compact 60% Keyboard',  'Discontinued line; sold out',
                 99.00,   0, 10, 'PERIPHERALS', 'Keystone', 1);

  -- Low stock — surfaces in /products/lowstock (stock < min_stock_level)
  upsert_product('HEAD-002', 'Open-back Headphones', 'Audiophile grade, replacement stock incoming',
                 179.00,  3, 10, 'PERIPHERALS', 'Auralink', 1);

  -- Unavailable — IS_AVAILABLE=0; should be filtered from active catalog
  upsert_product('LEGACY-001', 'Legacy VGA Cable',   'Discontinued; do not order',
                 4.99, 200, 50, 'ACCESSORIES', 'NodeOne', 0);

  -- Cheap product — bottom of price-range search
  upsert_product('CABLE-001', 'USB-C Cable 1m',      'Braided, 100W PD',
                 9.99, 500, 50, 'ACCESSORIES', 'NodeOne', 1);

  -- Premium / high-value product
  upsert_product('STUDIO-001', 'Studio Reference Monitor', 'Active near-field pair, 8" driver',
                 1999.00,  5,  2, 'AUDIO', 'Auralink', 1);

  -- Audio category to test category filter
  upsert_product('SPK-001', 'Bookshelf Speakers',    'Passive 5.25", walnut finish',
                 449.00, 14,  5, 'AUDIO', 'Auralink', 1);

  -- Storage category
  upsert_product('SSD-001', '2TB NVMe SSD',          'PCIe 4.0, DRAM cache',
                 189.00, 60, 15, 'STORAGE', 'NodeOne', 1);
  upsert_product('SSD-002', '4TB SATA SSD',          'Bulk storage tier',
                 269.00, 25, 10, 'STORAGE', 'NodeOne', 1);

  -- Unicode name — tests i18n through the API and DB
  upsert_product('TEA-001', 'Café au Lait Mug 350ml','Heat-retaining ceramic, double walled',
                 18.50, 200, 50, 'ACCESSORIES', 'PoshGoods', 1);

  -- Long description (CLOB-like load)
  upsert_product('GUIDE-001', 'Setup Field Guide',
      'A comprehensive 80-page handbook covering local-first development, '
      || 'profile-driven configuration, Spring Boot bootstrap, Flyway schema '
      || 'migrations, JWT issuance and rotation, distributed tracing through '
      || 'Tempo, structured logging into Loki, rate limiting via Bucket4j, '
      || 'and orchestrated saga patterns for cross-service workflows.',
      29.00,  40, 10, 'ACCESSORIES', 'PoshGoods', 1);
END;
/

PROMPT ===> Seeding orders covering every status + stale PENDING (idempotent on ORDER_NUMBER)
--------------------------------------------------------------------------------
DECLARE
  PROCEDURE upsert_order(
      p_order_number    VARCHAR2,
      p_user_email      VARCHAR2,
      p_status          VARCHAR2,
      p_created_offset  INTERVAL DAY TO SECOND,
      p_items           SYS.ODCIVARCHAR2LIST, -- list of "SKU:qty"
      p_notes           VARCHAR2 DEFAULT NULL
  ) IS
    v_existing  NUMBER;
    v_user_id   NUMBER;
    v_order_id  NUMBER;
    v_total     NUMBER := 0;
    v_created   TIMESTAMP;
    v_pair      VARCHAR2(50);
    v_sku       VARCHAR2(50);
    v_qty       NUMBER;
    v_pid       NUMBER;
    v_pname     VARCHAR2(100);
    v_pprice    NUMBER;
    v_colon_idx NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_existing FROM ORDERS WHERE ORDER_NUMBER = p_order_number;
    IF v_existing > 0 THEN
      DBMS_OUTPUT.PUT_LINE('Order ' || p_order_number || ' already exists, skipping');
      RETURN;
    END IF;

    SELECT ID INTO v_user_id FROM "USERS" WHERE EMAIL = p_user_email;
    v_created := SYSTIMESTAMP + p_created_offset;

    -- compute total from item list
    FOR i IN 1 .. p_items.COUNT LOOP
      v_pair := p_items(i);
      v_colon_idx := INSTR(v_pair, ':');
      v_sku := SUBSTR(v_pair, 1, v_colon_idx - 1);
      v_qty := TO_NUMBER(SUBSTR(v_pair, v_colon_idx + 1));
      SELECT PRICE INTO v_pprice FROM PRODUCTS WHERE SKU = v_sku;
      v_total := v_total + (v_pprice * v_qty);
    END LOOP;

    INSERT INTO ORDERS (
        ID, VERSION, ORDER_NUMBER, USER_ID, ORDER_STATUS, TOTAL_AMOUNT,
        SHIPPING_ADDRESS, NOTES, ESTIMATED_DELIVERY, CREATED_AT, CREATED_BY
    ) VALUES (
        ORDER_SEQ.NEXTVAL, 0, p_order_number, v_user_id, p_status, v_total,
        '99 Cart Avenue, Austin, TX 78701', p_notes,
        CASE
          WHEN p_status = 'SHIPPED' THEN v_created + INTERVAL '4' DAY
          WHEN p_status IN ('CONFIRMED', 'PENDING') THEN v_created + INTERVAL '7' DAY
          ELSE NULL
        END,
        v_created, 'system'
    ) RETURNING ID INTO v_order_id;

    -- insert items
    FOR i IN 1 .. p_items.COUNT LOOP
      v_pair := p_items(i);
      v_colon_idx := INSTR(v_pair, ':');
      v_sku := SUBSTR(v_pair, 1, v_colon_idx - 1);
      v_qty := TO_NUMBER(SUBSTR(v_pair, v_colon_idx + 1));
      SELECT ID, NAME, PRICE INTO v_pid, v_pname, v_pprice FROM PRODUCTS WHERE SKU = v_sku;
      INSERT INTO ORDER_ITEMS (
          ID, ORDER_ID, PRODUCT_ID, PRODUCT_NAME, PRODUCT_SKU,
          UNIT_PRICE, QUANTITY, DISCOUNT, CREATED_AT, CREATED_BY
      ) VALUES (
          ORDER_ITEM_SEQ.NEXTVAL, v_order_id, v_pid, v_pname, v_sku,
          v_pprice, v_qty, 0, v_created, 'system'
      );
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('Inserted order ' || p_order_number
        || ' (' || p_status || ', items=' || p_items.COUNT
        || ', total=' || v_total || ')');
  END;
BEGIN
  -- Recent PENDING — exercises /orders/by-status/PENDING
  upsert_order(
      'ORD-TEST-PENDING-1', 'customer@example.com',
      'PENDING', INTERVAL '-2' HOUR,
      SYS.ODCIVARCHAR2LIST('MOU-001:1', 'HUB-001:1'),
      'Awaiting confirmation');

  -- STALE PENDING — created 25 h ago; PendingOrderCleanupJob will sweep this
  upsert_order(
      'ORD-TEST-STALE-1', 'shopper1@example.com',
      'PENDING', INTERVAL '-25' HOUR,
      SYS.ODCIVARCHAR2LIST('SSD-001:1'),
      'Stale — should be auto-cancelled by cleanup job');

  -- CONFIRMED
  upsert_order(
      'ORD-TEST-CONFIRMED-1', 'customer@example.com',
      'CONFIRMED', INTERVAL '-1' DAY,
      SYS.ODCIVARCHAR2LIST('KBD-001:1', 'CABLE-001:2'),
      'Paid; warehouse picking');

  -- SHIPPED
  upsert_order(
      'ORD-TEST-SHIPPED-1', 'customer@example.com',
      'SHIPPED', INTERVAL '-3' DAY,
      SYS.ODCIVARCHAR2LIST('HEAD-001:1'),
      'In transit, tracking TRK-DEMO-001');

  -- CANCELLED — corresponds to a saga compensation example below
  upsert_order(
      'ORD-TEST-CANCELLED-1', 'poweruser@example.com',
      'CANCELLED', INTERVAL '-5' DAY,
      SYS.ODCIVARCHAR2LIST('STUDIO-001:1'),
      'Saga compensated due to shipping failure');

  -- A second DELIVERED order distinct from baseline ORD-SEED-0001
  upsert_order(
      'ORD-TEST-DELIVERED-1', 'customer@example.com',
      'DELIVERED', INTERVAL '-20' DAY,
      SYS.ODCIVARCHAR2LIST('MON-001:1'),
      'Signed for at door');

  -- Poweruser pagination — 5 more DELIVERED orders so /orders/user/{id} returns 6+ rows
  upsert_order(
      'ORD-PWR-001', 'poweruser@example.com',
      'DELIVERED', INTERVAL '-90' DAY,
      SYS.ODCIVARCHAR2LIST('SPK-001:1', 'CABLE-001:3'));
  upsert_order(
      'ORD-PWR-002', 'poweruser@example.com',
      'DELIVERED', INTERVAL '-75' DAY,
      SYS.ODCIVARCHAR2LIST('SSD-002:1'));
  upsert_order(
      'ORD-PWR-003', 'poweruser@example.com',
      'DELIVERED', INTERVAL '-60' DAY,
      SYS.ODCIVARCHAR2LIST('TEA-001:4'));
  upsert_order(
      'ORD-PWR-004', 'poweruser@example.com',
      'SHIPPED', INTERVAL '-2' DAY,
      SYS.ODCIVARCHAR2LIST('SSD-001:2', 'HUB-001:1'));
  upsert_order(
      'ORD-PWR-005', 'poweruser@example.com',
      'CONFIRMED', INTERVAL '-12' HOUR,
      SYS.ODCIVARCHAR2LIST('GUIDE-001:1', 'TEA-001:2'));
END;
/

PROMPT ===> Seeding saga rows (idempotent on SAGA_ID)
--------------------------------------------------------------------------------
DECLARE
  PROCEDURE upsert_saga(
      p_saga_id        VARCHAR2,
      p_order_number   VARCHAR2,
      p_state          VARCHAR2,
      p_step           VARCHAR2,
      p_error          VARCHAR2,
      p_log            CLOB
  ) IS
    v_count    NUMBER;
    v_order_id NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM ORDER_SAGA WHERE SAGA_ID = p_saga_id;
    IF v_count > 0 THEN
      DBMS_OUTPUT.PUT_LINE('Saga ' || p_saga_id || ' already exists, skipping');
      RETURN;
    END IF;
    SELECT ID INTO v_order_id FROM ORDERS WHERE ORDER_NUMBER = p_order_number;
    INSERT INTO ORDER_SAGA (
        ID, SAGA_ID, ORDER_ID, CURRENT_STATE, CURRENT_STEP,
        ERROR_MESSAGE, COMPENSATION_LOG, CREATED_AT, CREATED_BY
    ) VALUES (
        ORDER_SAGA_SEQ.NEXTVAL, p_saga_id, v_order_id, p_state, p_step,
        p_error, p_log, SYSTIMESTAMP - INTERVAL '5' DAY, 'system'
    );
    DBMS_OUTPUT.PUT_LINE('Inserted saga ' || p_saga_id || ' (state=' || p_state || ')');
  END;
BEGIN
  -- Successful saga for the delivered baseline order
  upsert_saga(
      'saga-demo-completed-1', 'ORD-SEED-0001',
      'COMPLETED', NULL, NULL,
      '[{"step":"RESERVE_STOCK","status":"OK"},'
      || '{"step":"CHARGE_PAYMENT","status":"OK"},'
      || '{"step":"SCHEDULE_SHIPPING","status":"OK"},'
      || '{"step":"COMPLETE_ORDER","status":"OK"}]');

  -- Compensated saga matching the CANCELLED order above
  upsert_saga(
      'saga-demo-compensated-1', 'ORD-TEST-CANCELLED-1',
      'COMPENSATED', 'SCHEDULE_SHIPPING',
      'ShippingService randomly failed (10% fault injection)',
      '[{"step":"RESERVE_STOCK","status":"OK"},'
      || '{"step":"CHARGE_PAYMENT","status":"OK"},'
      || '{"step":"SCHEDULE_SHIPPING","status":"FAILED","error":"ShippingException: random"},'
      || '{"step":"COMPENSATE_PAYMENT","status":"OK","refund_ref":"REFUND-DEMO-001"},'
      || '{"step":"COMPENSATE_STOCK","status":"OK","restored_qty":1}]');
END;
/

COMMIT;

PROMPT
PROMPT ============================================================================
PROMPT Scenario seed complete. Users / products / orders / sagas in place.
PROMPT
PROMPT QUICK INDEX OF WHAT TO TEST:
PROMPT
PROMPT  Auth:
PROMPT    Active user login           customer@example.com / Password123!
PROMPT    Inactive user login         inactive@example.com / Password123!     (expect 401)
PROMPT    Admin login                 vip@example.com / Password123!
PROMPT
PROMPT  Users (8+ rows):
PROMPT    GET /users?page=0&size=5    paginates across all seeded users
PROMPT    GET /users/email/customer@example.com   shows Deprecation+Sunset headers
PROMPT
PROMPT  Products (20 total):
PROMPT    GET /products                          full catalog
PROMPT    GET /products/category/AUDIO           filter
PROMPT    GET /products/lowstock                 returns KBD-002 (out) + HEAD-002 (low)
PROMPT    GET /products/search?minPrice=10&maxPrice=50  cheap tier
PROMPT
PROMPT  Orders:
PROMPT    GET /orders/user/<poweruser_id>        6+ orders, multi-page
PROMPT    GET /orders/by-status/PENDING          recent + stale
PROMPT    GET /orders/by-status/CANCELLED        ORD-TEST-CANCELLED-1
PROMPT    GET /orders/number/ORD-TEST-SHIPPED-1  by order number
PROMPT
PROMPT  Scheduled-job demo:
PROMPT    Wait for the top of the next hour — PendingOrderCleanupJob will
PROMPT    auto-cancel ORD-TEST-STALE-1 (age > 24 h) and emit OrderCancelledEvent.
PROMPT
PROMPT  Idempotency demo:
PROMPT    POST /orders with Idempotency-Key: my-key-1   (first call → 201)
PROMPT    Repeat                                         (replay with Idempotent-Replay:true)
PROMPT    Same key, different body                       (422 conflict)
PROMPT
PROMPT  Saga visibility:
PROMPT    SELECT saga_id, current_state, current_step, error_message
PROMPT    FROM apiservice.order_saga;
PROMPT
PROMPT All test users share password: Password123!
PROMPT ============================================================================
