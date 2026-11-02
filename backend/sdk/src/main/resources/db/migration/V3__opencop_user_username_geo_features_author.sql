ALTER TABLE opencop_user
ADD COLUMN username text;

UPDATE opencop_user
SET username =
    CASE
      WHEN email IS NOT NULL AND position('@' IN email) > 1 THEN split_part(email, '@', 1)
      WHEN email IS NOT NULL AND btrim(email) <> '' THEN btrim(email)
      WHEN display_name IS NOT NULL AND position('@' IN display_name) > 1 THEN split_part(display_name, '@', 1)
      WHEN display_name IS NOT NULL AND btrim(display_name) <> '' THEN btrim(display_name)
      ELSE idp_username
    END
WHERE username IS NULL;

ALTER TABLE opencop_user
ALTER COLUMN username SET NOT NULL;

ALTER TABLE geo.features
    ADD COLUMN author_id UUID NULL;

ALTER TABLE geo.features
    ADD CONSTRAINT fk_geo_features_author
        FOREIGN KEY (author_id) REFERENCES opencop_user(id);

CREATE INDEX geofeatures_author_created_at
    ON geo.features (author_id, created_at DESC);