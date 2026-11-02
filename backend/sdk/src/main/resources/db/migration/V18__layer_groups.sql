-- A layer group is a named container that holds multiple layers and grants its
-- members transitive role-based access to those layers.
CREATE TABLE iam.layer_group (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_layer_group_name UNIQUE (name)
);

-- Records which users belong to a layer group and with what management role.
CREATE TABLE iam.layer_group_member (
    layer_group_id UUID           NOT NULL REFERENCES iam.layer_group(id) ON DELETE CASCADE,
    user_id        UUID           NOT NULL REFERENCES opencop_user(id)    ON DELETE CASCADE,
    role           iam.layer_role NOT NULL,
    joined_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (layer_group_id, user_id)
);

CREATE INDEX idx_layer_group_member_user_id ON iam.layer_group_member (user_id);

-- Records which layers belong to a layer group and the role the group carries on
-- each layer. A layer may belong to multiple groups (no unique constraint on layer_id).
CREATE TABLE iam.layer_group_layer (
    layer_group_id UUID           NOT NULL REFERENCES iam.layer_group(id) ON DELETE CASCADE,
    layer_id       UUID           NOT NULL REFERENCES iam.layer(id)       ON DELETE CASCADE,
    role           iam.layer_role NOT NULL DEFAULT 'MEMBER',
    assigned_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (layer_group_id, layer_id)
);

CREATE INDEX idx_layer_group_layer_group_id ON iam.layer_group_layer (layer_group_id);
CREATE INDEX idx_layer_group_layer_layer_id ON iam.layer_group_layer (layer_id);
