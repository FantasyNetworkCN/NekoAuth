CREATE TABLE oauth_clients (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    client_id          VARCHAR(64)   NOT NULL,
    client_secret_hash VARCHAR(100)  NULL,
    name               VARCHAR(80)   NOT NULL,
    description        VARCHAR(500)  NULL,
    redirect_uris      VARCHAR(4000) NOT NULL,
    scopes             VARCHAR(512)  NOT NULL,
    public_client      BIT(1)        NOT NULL DEFAULT b'0',
    enabled            BIT(1)        NOT NULL DEFAULT b'1',
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_clients_client_id UNIQUE (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE oauth_authorization_codes (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    client_id             BIGINT        NOT NULL,
    user_id               BIGINT        NOT NULL,
    code_hash             VARCHAR(64)   NOT NULL,
    scopes                VARCHAR(512)  NOT NULL,
    redirect_uri          VARCHAR(2000) NOT NULL,
    code_challenge        VARCHAR(128)  NULL,
    code_challenge_method VARCHAR(16)   NULL,
    nonce                 VARCHAR(255)  NULL,
    expires_at            DATETIME(6)   NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    consumed_at           DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_codes_hash UNIQUE (code_hash),
    CONSTRAINT fk_oauth_codes_client FOREIGN KEY (client_id) REFERENCES oauth_clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_oauth_codes_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE oauth_refresh_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash   VARCHAR(64)  NOT NULL,
    client_id    BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    scopes       VARCHAR(512) NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NOT NULL,
    revoked_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_refresh_hash UNIQUE (token_hash),
    CONSTRAINT fk_oauth_refresh_client FOREIGN KEY (client_id) REFERENCES oauth_clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_oauth_refresh_active (user_id, client_id, revoked_at, expires_at),
    INDEX idx_oauth_refresh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE oauth_consents (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    client_id  BIGINT       NOT NULL,
    scopes     VARCHAR(512) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_consents_user_client UNIQUE (user_id, client_id),
    CONSTRAINT fk_oauth_consents_client FOREIGN KEY (client_id) REFERENCES oauth_clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_consents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
