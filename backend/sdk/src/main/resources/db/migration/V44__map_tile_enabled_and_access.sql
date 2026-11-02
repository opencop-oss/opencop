-- Enable/disable and access control for imagery / weather tile sources.
--
-- Note the schema split: map_tile lives in the public schema (V1 baseline) while every grant
-- table in this system lives in iam. The cross-schema foreign keys below are deliberate. Do not
-- "fix" them by moving the grant tables next to map_tile.

-- ---------------------------------------------------------------------------
-- Enable / disable
-- ---------------------------------------------------------------------------

-- Lets an admin take an imagery or weather tile out of circulation without deleting it,
-- which would lose its url, token and config. Weather overlays are map_tile rows with
-- config.type = 'WEATHER', so this one column covers both surfaces.
ALTER TABLE map_tile
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;

-- Disabled tiles are the rare case; the operator-facing list filters on enabled = true.
CREATE INDEX idx_map_tile_disabled
    ON map_tile (enabled)
    WHERE enabled = false;

-- ---------------------------------------------------------------------------
-- Access scope
-- ---------------------------------------------------------------------------

CREATE TYPE iam.map_tile_access_scope AS ENUM ('PRIVATE', 'GLOBAL');

-- Two different values, deliberately. Existing tiles backfill to GLOBAL because the whole catalog
-- is already in use and a PRIVATE backfill would empty it for every non-admin the moment this runs.
-- The lasting default is then PRIVATE, so a writer that omits the column gets the closed choice
-- rather than publishing a tile to everyone.
ALTER TABLE map_tile
    ADD COLUMN access_scope iam.map_tile_access_scope NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE map_tile
    ALTER COLUMN access_scope SET DEFAULT 'PRIVATE';

CREATE INDEX idx_map_tile_private_access_scope
    ON map_tile (access_scope)
    WHERE access_scope = 'PRIVATE';

-- ---------------------------------------------------------------------------
-- Grants
--
-- A row means the principal can see the tile. Nothing more: imagery is managed by global
-- admins, so there is no per-tile role ladder to express.
-- ---------------------------------------------------------------------------

-- user_id carries no foreign key, matching iam.layer_member and iam.server_user_access: users are
-- provisioned externally (SCIM / Keycloak), so a grant must not depend on the row already existing.
CREATE TABLE iam.map_tile_member (
    map_tile_id UUID        NOT NULL REFERENCES map_tile(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (map_tile_id, user_id)
);

CREATE INDEX idx_map_tile_member_user_id ON iam.map_tile_member (user_id);

CREATE TABLE iam.map_tile_user_group (
    map_tile_id   UUID        NOT NULL REFERENCES map_tile(id)       ON DELETE CASCADE,
    user_group_id UUID        NOT NULL REFERENCES iam.user_group(id) ON DELETE CASCADE,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (map_tile_id, user_group_id)
);

CREATE INDEX idx_map_tile_user_group_group_id ON iam.map_tile_user_group (user_group_id);
