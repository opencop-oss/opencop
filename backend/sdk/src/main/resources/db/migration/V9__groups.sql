-- Represents a named group of users. Groups can be assigned to layers with a role,
-- granting all members of the group access to that layer with the assigned role.
CREATE TABLE iam.user_group (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_group_name UNIQUE (name)
);

CREATE TABLE iam.user_group_member (
    group_id  UUID NOT NULL REFERENCES iam.user_group(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES opencop_user(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_user_group_member_user_id ON iam.user_group_member (user_id);

CREATE TABLE iam.group_layer_member (
    group_id    UUID NOT NULL REFERENCES iam.user_group(id) ON DELETE CASCADE,
    layer_id    UUID NOT NULL REFERENCES iam.layer(id) ON DELETE CASCADE,
    role        iam.layer_role NOT NULL DEFAULT 'MEMBER',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, layer_id)
);

CREATE INDEX idx_group_layer_member_layer_id ON iam.group_layer_member (layer_id);
CREATE INDEX idx_group_layer_member_group_id ON iam.group_layer_member (group_id);
