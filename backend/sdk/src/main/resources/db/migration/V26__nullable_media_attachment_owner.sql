-- Allow system-generated media attachments with no associated user (e.g. auto-attached RTSP
-- stream URLs from the processor). Mirrors the nullable author_id pattern on geo.features (V3).
ALTER TABLE media_attachment ALTER COLUMN creator_id DROP NOT NULL;
ALTER TABLE media_attachment ALTER COLUMN owner_id   DROP NOT NULL;
