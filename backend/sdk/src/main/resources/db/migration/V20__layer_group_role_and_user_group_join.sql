CREATE TYPE iam.layer_group_role AS ENUM ('OWNER', 'ADMIN', 'MAINTAINER', 'MEMBER');

CREATE TABLE iam.layer_group_user_group (
    layer_group_id UUID                  NOT NULL REFERENCES iam.layer_group(id) ON DELETE CASCADE,
    user_group_id  UUID                  NOT NULL REFERENCES iam.user_group(id)  ON DELETE CASCADE,
    role           iam.layer_group_role  NOT NULL,
    assigned_at    TIMESTAMPTZ           NOT NULL DEFAULT now(),
    PRIMARY KEY (layer_group_id, user_group_id)
);

CREATE INDEX idx_layer_group_user_group_user_group_id ON iam.layer_group_user_group (user_group_id);

DO $$
DECLARE
    unexpected TEXT;
BEGIN
    SELECT role::text INTO unexpected
    FROM iam.layer_group_member
    WHERE role::text NOT IN ('OWNER', 'ADMIN', 'EDITOR', 'MEMBER')
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Unexpected layer_group_member.role value: %', unexpected;
    END IF;
END;
$$;

ALTER TABLE iam.layer_group_member
    ALTER COLUMN role TYPE iam.layer_group_role
    USING CASE role::text
              WHEN 'OWNER'  THEN 'OWNER'::iam.layer_group_role
              WHEN 'ADMIN'  THEN 'ADMIN'::iam.layer_group_role
              WHEN 'EDITOR' THEN 'MAINTAINER'::iam.layer_group_role
              WHEN 'MEMBER' THEN 'MEMBER'::iam.layer_group_role
          END;

CREATE TYPE iam.layer_group_access_scope AS ENUM ('PRIVATE', 'GLOBAL');

ALTER TABLE iam.layer_group
    ADD COLUMN access_scope iam.layer_group_access_scope NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX idx_layer_group_global_access_scope
    ON iam.layer_group (access_scope)
    WHERE access_scope = 'GLOBAL';
