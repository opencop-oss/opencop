-- The non-secret part of the connection setup last submitted for a data source: the extractor it
-- chose and the values of the fields that extractor declares as text, so a later setup form can be
-- filled from them. Secret and file values are never stored. Null for a source whose setup was
-- never captured: one created through v1 or before this column existed.
ALTER TABLE profiles.data_source
    ADD COLUMN setup_prefill JSONB,
    ADD CONSTRAINT ck_data_source_setup_prefill_object
        CHECK (setup_prefill IS NULL OR jsonb_typeof(setup_prefill) = 'object');
