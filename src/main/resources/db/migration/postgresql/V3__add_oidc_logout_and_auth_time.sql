ALTER TABLE oauth_clients
    ADD COLUMN post_logout_redirect_uris VARCHAR(4000) NOT NULL DEFAULT '';

ALTER TABLE refresh_tokens
    ADD COLUMN auth_time TIMESTAMP WITH TIME ZONE;

ALTER TABLE oauth_authorization_codes
    ADD COLUMN auth_time TIMESTAMP WITH TIME ZONE;

ALTER TABLE oauth_refresh_tokens
    ADD COLUMN auth_time TIMESTAMP WITH TIME ZONE;
