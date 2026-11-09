ALTER TABLE admin_accounts
    ADD COLUMN consecutive_login_failures INT NOT NULL DEFAULT 0;
