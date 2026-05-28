-- ============================================================================
-- Manual schema application — emergency fallback if Flyway can't run for some
-- reason (network issues, Flyway version conflict, classpath problems, etc.).
--
-- Normally you do NOT need this — `mvn spring-boot:run` runs the same files
-- through Flyway automatically. This is only here as a safety net so you can
-- create the schema by hand if the normal path is broken.
--
-- Runs V1..V4 in order against the authoritative migration files at
-- src/main/resources/db/migration/. No SQL is duplicated here — this script
-- only chains the real Flyway files, so it can't drift from Flyway's behavior.
--
-- IMPORTANT: run scripts/cleanup-local-db.sql FIRST if the schema isn't empty,
-- otherwise these CREATE statements will fail with "name already used".
--
-- Usage:
--   cd /Users/somindrajaiswal/workspace/somi_personal/suru/APIDesign
--   sqlplus system/password@localhost:1521/FREEPDB1 @scripts/apply-schema-manually.sql
--
-- Or from inside sqlplus:
--   SQL> @scripts/apply-schema-manually.sql
-- ============================================================================

SET ECHO ON
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

PROMPT ===> Applying V1__initial_schema.sql
@src/main/resources/db/migration/V1__initial_schema.sql

PROMPT ===> Applying V2__application_exception.sql
@src/main/resources/db/migration/V2__application_exception.sql

PROMPT ===> Applying V3__refresh_tokens.sql
@src/main/resources/db/migration/V3__refresh_tokens.sql

PROMPT ===> Applying V4__shedlock.sql
@src/main/resources/db/migration/V4__shedlock.sql

COMMIT;

PROMPT
PROMPT All four migrations applied successfully.
PROMPT
PROMPT WARNING: Flyway will be confused on next boot because FLYWAY_SCHEMA_HISTORY
PROMPT          is empty but the tables exist. Two ways to recover:
PROMPT
PROMPT   Option A (preferred): set spring.flyway.baseline-on-migrate=true and
PROMPT   spring.flyway.baseline-version=4 for ONE boot via env vars:
PROMPT     SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
PROMPT     SPRING_FLYWAY_BASELINE_VERSION=4 \
PROMPT     mvn spring-boot:run
PROMPT
PROMPT   Option B: just drop everything (scripts/cleanup-local-db.sql) and let
PROMPT   Flyway re-apply normally. Loses any data but is the simplest path.
PROMPT
