ALTER TABLE oauth_clients
    ADD COLUMN post_logout_redirect_uris VARCHAR(4000) NOT NULL DEFAULT '' AFTER redirect_uris;

ALTER TABLE refresh_tokens
    ADD COLUMN auth_time DATETIME(6) NULL AFTER user_id;

ALTER TABLE oauth_authorization_codes
    ADD COLUMN auth_time DATETIME(6) NULL AFTER nonce;

ALTER TABLE oauth_refresh_tokens
    ADD COLUMN auth_time DATETIME(6) NULL AFTER scopes;
