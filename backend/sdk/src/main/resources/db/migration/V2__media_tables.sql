-- Media subsystem: media table, join tables, and avatar migration

CREATE TYPE media_content_type AS ENUM ('IMAGE', 'VIDEO', 'OTHER');
CREATE TYPE media_access AS ENUM ('LOGGED_IN', 'SCOPED');
CREATE TYPE media_usage AS ENUM ('AVATAR', 'LAYER_ICON', 'EVENT_DETAIL');

CREATE TABLE media (
    id                UUID PRIMARY KEY,
    blob_key          TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    content_type      media_content_type NOT NULL,
    size_bytes        BIGINT NOT NULL,
    access            media_access NOT NULL,
    creator_id        UUID NOT NULL REFERENCES opencop_user(id),
    owner_id          UUID NOT NULL REFERENCES opencop_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    mime_type         TEXT,
    attributes        JSONB,
    thumbnail_of      UUID REFERENCES media(id) ON DELETE CASCADE,
    usage             media_usage
);

CREATE INDEX idx_media_creator_id ON media (creator_id);
CREATE INDEX idx_media_owner_id ON media (owner_id);
CREATE INDEX idx_media_content_type_created ON media (content_type, created_at DESC);
CREATE INDEX idx_media_created_at ON media (created_at DESC);
CREATE INDEX idx_media_thumbnail_of ON media (thumbnail_of) WHERE thumbnail_of IS NOT NULL;

-- Layer-to-media join table (KMZ icons)
CREATE TABLE layer_media (
    layer_id  UUID NOT NULL,
    media_id  UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    PRIMARY KEY (layer_id, media_id)
);

-- Event-to-media join table (event attachments)
CREATE TABLE event_media (
    event_id  TEXT NOT NULL,
    media_id  UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, media_id)
);

-- Add avatar_media_id to user preferences (replaces avatar_url)
ALTER TABLE opencop_user_preference
    ADD COLUMN avatar_media_id UUID REFERENCES media(id) ON DELETE SET NULL;

ALTER TABLE opencop_user_preference
    DROP COLUMN avatar_url;
