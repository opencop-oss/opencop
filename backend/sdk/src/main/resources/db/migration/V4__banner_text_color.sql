ALTER TABLE banner
ADD COLUMN text_color TEXT;

UPDATE banner
SET text_color = '#FFFFFF'
WHERE text_color IS NULL;
