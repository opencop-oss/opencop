ALTER TABLE iam.user_group
    ADD COLUMN idp_issuer TEXT,
    ADD COLUMN idp_group_id TEXT;

CREATE UNIQUE INDEX uk_user_group_idp_issuer_group_id
    ON iam.user_group (idp_issuer, idp_group_id)
    WHERE idp_issuer IS NOT NULL
      AND idp_group_id IS NOT NULL;

ALTER TABLE iam.user_group
    DROP CONSTRAINT uk_user_group_name;

CREATE UNIQUE INDEX uk_user_group_admin_name
    ON iam.user_group (name)
    WHERE idp_issuer IS NULL;

CREATE UNIQUE INDEX uk_user_group_issuer_name
    ON iam.user_group (idp_issuer, name)
    WHERE idp_issuer IS NOT NULL;
