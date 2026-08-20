ALTER TABLE users
    ADD COLUMN activation_token_hash CHAR(64) NULL,
    ADD COLUMN activation_token_expires_at DATETIME NULL,
    ADD COLUMN activated_at DATETIME NULL;

ALTER TABLE users
    ADD INDEX idx_users_activation_token_hash (activation_token_hash);
