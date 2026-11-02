CREATE TYPE iam.data_source_group_role AS ENUM ('OWNER', 'ADMIN', 'MAINTAINER', 'MEMBER');

CREATE TABLE iam.data_source_group (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_data_source_group_name UNIQUE (name)
);

CREATE TABLE iam.data_source_group_member (
    data_source_group_id UUID                       NOT NULL REFERENCES iam.data_source_group(id) ON DELETE CASCADE,
    user_id              UUID                       NOT NULL REFERENCES opencop_user(id)          ON DELETE CASCADE,
    role                 iam.data_source_group_role NOT NULL,
    joined_at            TIMESTAMPTZ                NOT NULL DEFAULT now(),
    PRIMARY KEY (data_source_group_id, user_id)
);

CREATE INDEX idx_data_source_group_member_user_id
    ON iam.data_source_group_member (user_id);

CREATE TABLE iam.data_source_group_data_source (
    data_source_group_id UUID            NOT NULL REFERENCES iam.data_source_group(id) ON DELETE CASCADE,
    data_source_id       UUID            NOT NULL REFERENCES profiles.data_source(id)  ON DELETE CASCADE,
    role                 iam.server_role NOT NULL DEFAULT 'VIEWER',
    assigned_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (data_source_group_id, data_source_id)
);

CREATE INDEX idx_data_source_group_data_source_data_source_id
    ON iam.data_source_group_data_source (data_source_id);

CREATE INDEX idx_data_source_group_data_source_group_id
    ON iam.data_source_group_data_source (data_source_group_id);

CREATE TABLE iam.data_source_group_user_group (
    data_source_group_id UUID                       NOT NULL REFERENCES iam.data_source_group(id) ON DELETE CASCADE,
    user_group_id        UUID                       NOT NULL REFERENCES iam.user_group(id)        ON DELETE CASCADE,
    role                 iam.data_source_group_role NOT NULL,
    assigned_at          TIMESTAMPTZ                NOT NULL DEFAULT now(),
    PRIMARY KEY (data_source_group_id, user_group_id)
);

CREATE INDEX idx_data_source_group_user_group_user_group_id
    ON iam.data_source_group_user_group (user_group_id);

CREATE TABLE iam.layer_data_source_group (
    layer_id             UUID        NOT NULL REFERENCES iam.layer(id)             ON DELETE CASCADE,
    data_source_group_id UUID        NOT NULL REFERENCES iam.data_source_group(id) ON DELETE CASCADE,
    assigned_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (layer_id, data_source_group_id)
);

CREATE INDEX idx_layer_data_source_group_layer_id
    ON iam.layer_data_source_group (layer_id);

CREATE INDEX idx_layer_data_source_group_group_id
    ON iam.layer_data_source_group (data_source_group_id);

CREATE OR REPLACE FUNCTION iam.effective_source_roles(
    p_user_id UUID,
    p_source_id UUID DEFAULT NULL,
    p_include_archived_sources BOOLEAN DEFAULT FALSE
)
RETURNS TABLE (
    source_id UUID,
    role iam.server_role,
    via TEXT
)
LANGUAGE sql
STABLE
AS $$
WITH source AS (
    SELECT id
    FROM profiles.data_source
    WHERE (p_source_id IS NULL OR id = p_source_id)
      AND (p_include_archived_sources OR archived_at IS NULL)
),
user_groups AS (
    SELECT group_id
    FROM iam.user_group_member
    WHERE user_id = p_user_id
),
reachable_layer_roles AS (
    SELECT l.id AS layer_id, lm.role AS role
    FROM iam.layer l
    JOIN iam.layer_member lm
      ON lm.layer_id = l.id
     AND lm.user_id = p_user_id
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state

    UNION ALL

    SELECT l.id AS layer_id, 'MEMBER'::iam.layer_role AS role
    FROM iam.layer l
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state
      AND l.access_scope = 'GLOBAL'::iam.layer_access_scope

    UNION ALL

    SELECT l.id AS layer_id, glm.role AS role
    FROM iam.layer l
    JOIN iam.group_layer_member glm
      ON glm.layer_id = l.id
    JOIN user_groups ug
      ON ug.group_id = glm.group_id
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state

    UNION ALL

    SELECT l.id AS layer_id, lgl.role AS role
    FROM iam.layer l
    JOIN iam.layer_group_layer lgl
      ON lgl.layer_id = l.id
    JOIN iam.layer_group_member lgm
      ON lgm.layer_group_id = lgl.layer_group_id
     AND lgm.user_id = p_user_id
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state

    UNION ALL

    SELECT l.id AS layer_id, lgl.role AS role
    FROM iam.layer l
    JOIN iam.layer_group_layer lgl
      ON lgl.layer_id = l.id
    JOIN iam.layer_group_user_group lgug
      ON lgug.layer_group_id = lgl.layer_group_id
    JOIN user_groups ug
      ON ug.group_id = lgug.user_group_id
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state

    UNION ALL

    SELECT l.id AS layer_id, lgl.role AS role
    FROM iam.layer l
    JOIN iam.layer_group_layer lgl
      ON lgl.layer_id = l.id
    JOIN iam.layer_group lg
      ON lg.id = lgl.layer_group_id
    WHERE l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state
      AND lg.access_scope = 'GLOBAL'::iam.layer_group_access_scope
),
roles AS (
    SELECT source.id AS source_id, sua.role, 'DIRECT' AS via
    FROM source
    JOIN iam.server_user_access sua
      ON sua.server_id = source.id
     AND sua.user_id = p_user_id

    UNION ALL

    SELECT source.id AS source_id, dsgds.role, 'INDIRECT' AS via
    FROM source
    JOIN iam.data_source_group_data_source dsgds
      ON dsgds.data_source_id = source.id
    JOIN iam.data_source_group_member dsgm
      ON dsgm.data_source_group_id = dsgds.data_source_group_id
     AND dsgm.user_id = p_user_id

    UNION ALL

    SELECT source.id AS source_id, dsgds.role, 'INDIRECT' AS via
    FROM source
    JOIN iam.data_source_group_data_source dsgds
      ON dsgds.data_source_id = source.id
    JOIN iam.data_source_group_user_group dsgug
      ON dsgug.data_source_group_id = dsgds.data_source_group_id
    JOIN user_groups ug
      ON ug.group_id = dsgug.user_group_id

    UNION ALL

    SELECT source.id AS source_id, sla.role, 'INDIRECT' AS via
    FROM source
    JOIN iam.server_layer_access sla
      ON sla.server_id = source.id
    JOIN reachable_layer_roles rl
      ON rl.layer_id = sla.layer_id

    UNION ALL

    SELECT source.id AS source_id, dsgds.role, 'INDIRECT' AS via
    FROM source
    JOIN iam.data_source_group_data_source dsgds
      ON dsgds.data_source_id = source.id
    JOIN iam.layer_data_source_group ldsg
      ON ldsg.data_source_group_id = dsgds.data_source_group_id
    JOIN reachable_layer_roles rl
      ON rl.layer_id = ldsg.layer_id
)
SELECT roles.source_id,
       MIN(roles.role) AS role,
       CASE
         WHEN bool_or(roles.via = 'DIRECT') AND bool_or(roles.via = 'INDIRECT') THEN 'BOTH'
         WHEN bool_or(roles.via = 'DIRECT') THEN 'DIRECT'
         ELSE 'INDIRECT'
       END AS via
FROM roles
GROUP BY roles.source_id
$$;
