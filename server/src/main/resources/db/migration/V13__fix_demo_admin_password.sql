-- Demo credentials for the local MVP environment: admin / password.
-- The previous migration contained a BCrypt value that did not match either
-- documented demonstration password, which made the protected API unusable.
UPDATE users
SET password = '$2a$10$zS2RIdZCHxLWdi548DU.R.qhMlkPwsyR1qfLUwuvVwY4twlt0s.HK'
WHERE username = 'admin';
