-- Metadata columns for visibility/accountability on Layers, Data Sources, and Mappers.
-- Columns added by this migration are nullable so existing rows remain valid; no historical
-- metadata is backfilled.

-- iam.layer: add created_by, updated_at, updated_by, last_event_at
-- created_at already exists (managed by @CreationTimestamp)
ALTER TABLE iam.layer
    ADD COLUMN created_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN updated_at    timestamptz,
    ADD COLUMN updated_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN last_event_at timestamptz;

-- profiles.data_source: add created_by, updated_by, last_event_at
-- created_at and updated_at already exist
ALTER TABLE profiles.data_source
    ADD COLUMN created_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN updated_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN last_event_at timestamptz;

-- profiles.data_mapper: add all five audit columns
-- Composite PK (id, version): mapper *content* is versioned — an edit inserts a new row rather
-- than rewriting one — but lifecycle and audit metadata stay mutable. Archiving updates every
-- version of a mapper in place, so updated_at/updated_by can move on a row after it is written.
-- created_at/created_by are carried forward from the first version so authorship survives edits.
ALTER TABLE profiles.data_mapper
    ADD COLUMN created_at    timestamptz,
    ADD COLUMN created_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN updated_at    timestamptz,
    ADD COLUMN updated_by    uuid REFERENCES opencop_user(id),
    ADD COLUMN last_event_at timestamptz;
