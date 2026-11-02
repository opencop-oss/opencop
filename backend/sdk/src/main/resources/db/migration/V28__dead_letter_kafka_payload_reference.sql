ALTER TABLE processor.processor_dead_letter_record
    ADD COLUMN payload_expires_at TIMESTAMPTZ;

UPDATE processor.processor_dead_letter_record
   SET payload_expires_at = indexed_at + INTERVAL '7 days'
 WHERE payload_expires_at IS NULL;

ALTER TABLE processor.processor_dead_letter_record
    ALTER COLUMN payload_expires_at SET NOT NULL,
    ALTER COLUMN payload_sha256 DROP NOT NULL,
    ALTER COLUMN payload_size_bytes DROP NOT NULL;
