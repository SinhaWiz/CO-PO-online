-- Thresholds' PRIMARY KEY (type) was declared directly inside V1's CREATE TABLE
-- statement, which means it's only actually applied when V1 runs for real - a fresh
-- install. Anyone adopting an existing desktop-created database has V1 skipped
-- entirely by Flyway's baseline (see application.yml's flyway.baseline-version
-- comment), so their Thresholds table - created by the desktop app's own schema.sql,
-- which never had a primary key on this table at all - never gets it added. This
-- migration backfills it for that case.
--
-- Written as a conditional ALTER rather than a bare one because it also has to be a
-- genuine no-op on a fresh install, where V1 already added this exact primary key -
-- MySQL has no "ADD PRIMARY KEY IF NOT EXISTS", and a second attempt to add the same
-- key would fail the migration outright.
--
-- Requires every Thresholds.type value to already be unique. If you're adopting an
-- existing database, run migration/preflight-check.sql against it first and resolve
-- any duplicates it reports - this ALTER fails immediately if any remain.
SET @pk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'Thresholds'
      AND CONSTRAINT_TYPE = 'PRIMARY KEY'
);

SET @ddl = IF(@pk_exists = 0, 'ALTER TABLE Thresholds ADD PRIMARY KEY (type)', 'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
