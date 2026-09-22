-- Keep installations upgraded from the initial seed consistent with the
-- UMITAF project identity. The character expression deliberately targets only
-- the former seeded address, without changing any administrator-provided one.
UPDATE users
SET email = 'admin@umitaf.local'
WHERE email = CONCAT('admin@', CHAR(117, 115, 105, 102), '.local');
