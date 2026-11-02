ALTER TABLE opencop_user
    ADD COLUMN session_generation bigint NOT NULL DEFAULT 0,
    ADD COLUMN account_access_revision bigint NOT NULL DEFAULT 0;
