ALTER TABLE profiles.data_delimiter
    ADD COLUMN editable boolean NOT NULL DEFAULT true;

ALTER TABLE profiles.data_profile
    ADD COLUMN editable boolean NOT NULL DEFAULT true;

UPDATE profiles.data_profile  SET editable = false WHERE id = '8b8a3913-2268-4630-af6d-1a4d5d34f577';
UPDATE profiles.data_mapper    SET editable = false WHERE id = '24e7279b-abff-4b68-a0c3-2225e6262ed6';
UPDATE profiles.data_mapper    SET editable = false WHERE id = '4017d49f-2a68-4900-8e50-2dbb3bd435df';
UPDATE profiles.data_mapper    SET editable = false WHERE id = 'f6c26161-4cdc-4122-b781-4a2cad937cfe';
UPDATE profiles.data_delimiter SET editable = false WHERE id = 'd006c0ad-f84a-4e69-b7cb-3f58bcf4ecb6';
