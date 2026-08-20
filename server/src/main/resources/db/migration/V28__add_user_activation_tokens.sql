ALTER TABLE users
    ADD COLUMN activation_token_hash CHAR(64) NULL AFTER locked_until,
    ADD COLUMN activation_token_expires_at DATETIME NULL AFTER activation_token_hash,
    ADD COLUMN activated_at DATETIME NULL AFTER activation_token_expires_at,
    ADD INDEX idx_users_activation_token_hash (activation_token_hash);
