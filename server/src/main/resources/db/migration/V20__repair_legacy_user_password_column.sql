-- Some existing local MVP databases were created before V18 and have a
-- `users.password` column although the application reads `password_hash`.
-- Keep this migration idempotent so both schema variants can be started.
SET @has_legacy_password := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'password'
);

SET @repair_sql := IF(
    @has_legacy_password > 0,
    'ALTER TABLE users CHANGE COLUMN password password_hash VARCHAR(255) NOT NULL',
    'SELECT 1'
);

PREPARE repair_statement FROM @repair_sql;
EXECUTE repair_statement;
DEALLOCATE PREPARE repair_statement;
