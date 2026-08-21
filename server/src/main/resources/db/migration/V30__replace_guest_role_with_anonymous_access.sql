-- Guest access is an anonymous session mode, not an assignable user role.
UPDATE users
SET role_id = (SELECT id FROM roles WHERE code = 'VIEWER')
WHERE role_id IN (SELECT id FROM roles WHERE code = 'GUEST');

DELETE FROM roles WHERE code = 'GUEST';
