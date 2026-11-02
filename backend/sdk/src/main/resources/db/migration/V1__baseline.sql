-- V1__baseline.sql

-- Extensions required by the schema
CREATE EXTENSION IF NOT EXISTS pgcrypto;
${postgisExtensionSql}

-- Schemas
CREATE SCHEMA profiles;
CREATE SCHEMA iam;
CREATE SCHEMA geo;

-- =========================
-- Public types
-- =========================

-- Type of banner (where/how it's used in the app)
CREATE TYPE banner_kind AS ENUM (
  'HEADER',      -- Appears at the top of the app
  'FOOTER',      -- Appears at the bottom
  'MODAL',       -- Shown as a popup
  'SYSTEM'       -- Internal/system banner (future use)
);

-- How the content should be interpreted by the frontend
CREATE TYPE banner_format AS ENUM (
  'PLAIN',       -- Plain text only
  'MARKDOWN',    -- Markdown formatting allowed
  'HTML'         -- Raw HTML (use carefully)
);

CREATE TYPE measurement_system AS ENUM (
  'METRIC',
  'IMPERIAL'
);

CREATE TYPE temperature_unit AS ENUM (
  'CELSIUS',
  'FAHRENHEIT'
);

CREATE TYPE notification_severity AS ENUM (
  'NONE',
  'INFO',
  'WARNING',
  'SUCCESS',
  'ERROR',
  'IMPORTANT',
  'CRITICAL'
);

CREATE TYPE notification_status AS ENUM (
  'CREATED',
  'PENDING',
  'SENT',
  'FAILED'
);

CREATE TYPE map_zoom_behavior AS ENUM (
  'CENTER',
  'MOUSE'
);

-- =========================
-- Profiles schema types
-- =========================

CREATE TYPE profiles.transport_kind AS ENUM ('TCP', 'UDP', 'KAFKA');
CREATE TYPE profiles.access_mode as ENUM ('R', 'W', 'RW');

-- =========================
-- IAM schema types
-- =========================

CREATE TYPE iam.layer_role AS ENUM ('OWNER','ADMIN','EDITOR','MEMBER');
CREATE TYPE iam.server_role AS ENUM ('OWNER','ADMIN','VIEWER');

-- =========================
-- Public tables
-- =========================

CREATE TABLE banner (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name            text NOT NULL,

    -- Defines where/how the banner is used in the app
    kind            banner_kind NOT NULL DEFAULT 'HEADER',

    -- Whether this banner is currently active
    enabled         BOOLEAN NOT NULL DEFAULT true,

    -- Optional: when the banner should start being visible
    starts_at       TIMESTAMPTZ,

    -- Optional: when the banner should stop being visible
    ends_at         TIMESTAMPTZ,

    -- Visual styling hint (e.g. "#FF0000", "warning", "info")
    -- Lets frontend apply different styles
    color           TEXT,

    -- Defines how content should be rendered (plain, markdown, etc.)
    format          banner_format NOT NULL DEFAULT 'PLAIN',

    -- Whether users can close/dismiss the banner
    dismissible     BOOLEAN NOT NULL DEFAULT true,

    -- Optional: clicking banner redirects to this URL
    link_url        TEXT,

    -- Used if multiple banners are enabled; higher priority shown first
    priority        INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE banner_translation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The banner this translation belongs to
    banner_id       UUID NOT NULL REFERENCES banner(id) ON DELETE CASCADE,

    -- BCP-47 tag like "en-US", "fr-CA"
    -- Validated in backend
    locale          TEXT NOT NULL,

    -- The translated banner message
    content         TEXT NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT banner_translation_unique
        UNIQUE (banner_id, locale)
    -- Ensures one translation per locale per banner
);

CREATE TABLE map_tile (
    id          uuid PRIMARY KEY,
    is_primary  boolean NOT NULL DEFAULT false,
    name        text NOT NULL UNIQUE,
    description text,
    url         text NOT NULL,
    kind        text NOT NULL,
    config      jsonb NOT NULL DEFAULT '{}'::jsonb,
    token_ref    text
);

CREATE UNIQUE INDEX map_tile_single_primary
    ON map_tile ((is_primary))
WHERE is_primary = true;

CREATE INDEX map_tile_kind_idx ON map_tile (kind);
CREATE INDEX map_tile_config_gin_idx ON map_tile USING gin (config);

CREATE OR REPLACE PROCEDURE set_primary_map_tile(p_id uuid)
LANGUAGE plpgsql
AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM map_tile WHERE id = p_id) THEN
    RAISE EXCEPTION 'map_tile % does not exist', p_id;
END IF;

  -- Prevent concurrent sessions
  LOCK TABLE map_tile IN EXCLUSIVE MODE;

  -- First, clear any existing primary
UPDATE map_tile
SET is_primary = false
WHERE is_primary = true
  AND id <> p_id;

-- Set the target as primary
UPDATE map_tile
SET is_primary = true
WHERE id = p_id;
END;
$$;

CREATE TABLE opencop_user (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Link back to the IdP / SCIM payload
    idp_user_id         text NOT NULL,
    idp_username        text NOT NULL,
    idp_issuer          text NOT NULL,

    -- OpenCOP identity fields
    email               text,
    first_name          text,
    last_name           text,
    display_name        text,

    active              boolean NOT NULL DEFAULT true,
    administrator       boolean NOT NULL DEFAULT false,

    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,

    CONSTRAINT uk_user_idp_issuer_idp_user
      UNIQUE (idp_issuer, idp_user_id),

    CONSTRAINT uk_user_idp_issuer_username
      UNIQUE (idp_issuer, idp_username)
);

CREATE INDEX idx_opencop_user_email  ON opencop_user (email);
CREATE INDEX idx_opencop_user_active ON opencop_user (active);

CREATE TABLE opencop_map_capture (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES opencop_user(id) ON DELETE CASCADE,
    name              text NOT NULL,

    bearing           double precision NOT NULL,
    latitude          double precision NOT NULL,
    longitude         double precision NOT NULL,
    pitch             double precision NOT NULL,
    zoom              double precision NOT NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);

CREATE TABLE opencop_user_preference (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES opencop_user(id) ON DELETE CASCADE,
    avatar_url          text,

    locale              text NOT NULL DEFAULT 'en',
    measurement_system  measurement_system NOT NULL DEFAULT 'METRIC',
    temperature_unit    temperature_unit NOT NULL DEFAULT 'CELSIUS',
    timezone            text NOT NULL DEFAULT 'UTC',
    zoom_behavior       map_zoom_behavior NOT NULL DEFAULT 'CENTER',

    map_capture_id      UUID REFERENCES opencop_map_capture(id) ON DELETE SET NULL,
    map_tile_id         UUID REFERENCES map_tile(id) ON DELETE SET NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ
);

CREATE TABLE opencop_group (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_group_id        text NOT NULL UNIQUE,
    name                text NOT NULL,
    description         text,
    active              boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_opencop_group_name ON opencop_group (name);
CREATE INDEX idx_opencop_group_active ON opencop_group (active);

CREATE TABLE opencop_user_groups (
    user_id             uuid NOT NULL,
    group_id            uuid NOT NULL,
    assigned_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, group_id),
    FOREIGN KEY (user_id) REFERENCES opencop_user(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES opencop_group(id) ON DELETE CASCADE
);

CREATE INDEX idx_opencop_user_groups_user_id ON opencop_user_groups (user_id);
CREATE INDEX idx_opencop_user_groups_group_id ON opencop_user_groups (group_id);

CREATE TABLE opencop_notification (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id    UUID,

    issuer_user_id    UUID NOT NULL REFERENCES opencop_user(id),
    target_user_id    UUID NOT NULL REFERENCES opencop_user(id),

    type              TEXT NOT NULL,

    severity          notification_severity NOT NULL,
    status            notification_status NOT NULL,

    content           JSONB NOT NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at           TIMESTAMPTZ
);

-- =========================
-- Profiles schema tables
-- =========================

CREATE TABLE profiles.data_delimiter (
    id                 uuid PRIMARY KEY,
    name               TEXT NOT NULL UNIQUE,
    mode               text,
    delimiter          text
);

CREATE TABLE profiles.data_mapper (
    id                      uuid NOT NULL,
    version                 int  NOT NULL,
    name                    text NOT NULL,
    description             text,
    editable                boolean NOT NULL DEFAULT true,
    mapper                  jsonb NOT NULL,

    sample_payload          bytea,
    sample_payload_filename text,

    PRIMARY KEY (id, version)
);

-- Fast "latest version by id"
CREATE INDEX idx_data_mapper_id_version_desc
    ON profiles.data_mapper (id, version DESC);

CREATE INDEX idx_data_mapper_name
    ON profiles.data_mapper (name);

CREATE TABLE profiles.data_profile (
    id                 uuid PRIMARY KEY,
    name               text NOT NULL UNIQUE,
    delimiter_id       uuid NOT NULL,
    mapper_id          uuid NOT NULL,
    mapper_version     int  NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_data_profile_delimiter
       FOREIGN KEY (delimiter_id)
           REFERENCES profiles.data_delimiter (id),

    CONSTRAINT fk_data_profile_mapper
       FOREIGN KEY (mapper_id, mapper_version)
           REFERENCES profiles.data_mapper (id, version)
);

-- Data source = used by core for metadata about a server, e.g. a TAK server (host, port, credentials, etc.)
-- Runtime connection state (status, lastMessageAt) is owned by connect service (in-memory for now, maybe cached in the future)
-- `enabled` = user intent: whether the connection should auto-start (false = user explicitly stopped it)
CREATE TABLE profiles.data_source (
    id                 uuid PRIMARY KEY,
    name               text NOT NULL UNIQUE,
    tenant             text,
    provider           profiles.transport_kind,
    host               text,
    port               integer,
    data_profile_id    uuid NOT NULL,
    connection_spec    jsonb NOT NULL,
    enabled            boolean NOT NULL DEFAULT true,
    access             profiles.access_mode NOT NULL DEFAULT 'R',
    assigned_instance  text,  -- Connect instance ID for horizontal scaling (nullable = unassigned)
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_data_source_data_profile
      FOREIGN KEY (data_profile_id)
          REFERENCES profiles.data_profile (id)
);

CREATE INDEX ds_tenant_idx     ON profiles.data_source (tenant);
CREATE INDEX ds_provider_idx   ON profiles.data_source (provider);
CREATE INDEX ds_host_port_idx  ON profiles.data_source (host, port);

-- Auto-touch updated_at
CREATE OR REPLACE FUNCTION profiles.touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
RETURN NEW;
END
$$;

CREATE TRIGGER trg_ds_touch
    BEFORE UPDATE ON profiles.data_source
    FOR EACH ROW
    EXECUTE FUNCTION profiles.touch_updated_at();

-- =========================
-- IAM schema tables
-- =========================

-- Layers
CREATE TABLE iam.layer (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Layer membership
CREATE TABLE iam.layer_member (
    layer_id    UUID NOT NULL REFERENCES iam.layer(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    role        iam.layer_role NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (layer_id, user_id)
);

CREATE INDEX idx_layer_member_user_id ON iam.layer_member (user_id);
CREATE INDEX idx_layer_member_layer_role ON iam.layer_member (layer_id, role);

-- Server access 
CREATE TABLE iam.server_layer_access (
    server_id   UUID NOT NULL,
    layer_id    UUID NOT NULL REFERENCES iam.layer(id) ON DELETE CASCADE,
    role        iam.server_role NOT NULL, -- VIEWER | ADMIN | OWNER
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (server_id, layer_id)
);

CREATE INDEX idx_sta_by_server ON iam.server_layer_access (server_id);
CREATE INDEX idx_sta_by_layer ON iam.server_layer_access (layer_id);
CREATE INDEX idx_sta_server_role ON iam.server_layer_access (server_id, role);

-- User access 
CREATE TABLE iam.server_user_access (
    server_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    role        iam.server_role NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (server_id, user_id)
);

CREATE INDEX idx_sua_by_server ON iam.server_user_access (server_id);
CREATE INDEX idx_sua_by_user_id ON iam.server_user_access (user_id);
CREATE INDEX idx_sua_server_role ON iam.server_user_access (server_id, role);

CREATE INDEX idx_sua_server_user ON iam.server_user_access (server_id, user_id);
CREATE INDEX idx_sla_server_layer ON iam.server_layer_access (server_id, layer_id);
CREATE INDEX idx_lm_user_layer ON iam.layer_member (user_id, layer_id);

-- =========================
-- Geo schema tables
-- =========================

CREATE TABLE geo.features (
  id           UUID PRIMARY KEY,
  feature_kind text,
  source       UUID NOT NULL,
  object_id    VARCHAR(255) NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  start_at     timestamptz,
  stale_at     timestamptz,
  geom         ${geoFeaturesGeomType},
  payload      jsonb NOT NULL,
  CHECK (jsonb_typeof(payload)='object'),
  CHECK (payload ? 'type'),
  CHECK ((payload->>'type') IN ('Feature','Event'))
);

${geoFeaturesGeomIndexSql}
CREATE INDEX geofeatures_event_time       ON geo.features (object_id, created_at);
CREATE INDEX geofeatures_created_at_brin  ON geo.features USING BRIN (created_at);
CREATE INDEX geofeatures_feature_kind     ON geo.features (feature_kind);
CREATE INDEX geofeatures_source           ON geo.features (source);
CREATE INDEX geofeatures_payload_gin      ON geo.features USING GIN (payload jsonb_path_ops);

CREATE TABLE geo.remarks (
    id UUID PRIMARY KEY,
    object_id VARCHAR(255),
    content VARCHAR NOT NULL,
    user_id UUID REFERENCES opencop_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz
);

CREATE INDEX idx_remarks_event_created
    ON geo.remarks(object_id, created_at DESC);
