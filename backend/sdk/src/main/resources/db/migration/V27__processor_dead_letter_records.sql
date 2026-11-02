CREATE SCHEMA processor;

CREATE TABLE processor.processor_dead_letter_record (
    id UUID PRIMARY KEY,
    state TEXT NOT NULL DEFAULT 'OPEN',
    state_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    stage TEXT NOT NULL,
    failure_kind TEXT NOT NULL,
    original_topic TEXT NOT NULL,
    original_partition INTEGER,
    original_offset BIGINT,
    dlt_topic TEXT NOT NULL,
    dlt_partition INTEGER NOT NULL,
    dlt_offset BIGINT NOT NULL,
    exception_class TEXT NOT NULL,
    exception_message TEXT NOT NULL,
    replay_count INTEGER NOT NULL DEFAULT 0,
    context_json TEXT NOT NULL,
    headers_json TEXT NOT NULL,
    payload_ref TEXT NOT NULL,
    payload_sha256 TEXT NOT NULL,
    payload_size_bytes BIGINT NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT processor_dead_letter_record_dlt_coordinate_key
        UNIQUE (dlt_topic, dlt_partition, dlt_offset)
);

CREATE TABLE processor.processor_dead_letter_source_context (
    failure_id UUID PRIMARY KEY REFERENCES processor.processor_dead_letter_record(id) ON DELETE CASCADE,
    source_id UUID NOT NULL
);

CREATE TABLE processor.processor_dead_letter_job_context (
    failure_id UUID PRIMARY KEY REFERENCES processor.processor_dead_letter_record(id) ON DELETE CASCADE,
    job_id UUID NOT NULL
);

CREATE TABLE processor.processor_dead_letter_mapper_context (
    failure_id UUID PRIMARY KEY REFERENCES processor.processor_dead_letter_record(id) ON DELETE CASCADE,
    mapper_id UUID NOT NULL
);

CREATE TABLE processor.processor_dead_letter_import_context (
    failure_id UUID PRIMARY KEY REFERENCES processor.processor_dead_letter_record(id) ON DELETE CASCADE,
    import_id UUID NOT NULL
);

CREATE INDEX idx_processor_dead_letter_record_original_coordinate
    ON processor.processor_dead_letter_record (original_topic, original_partition, original_offset);

CREATE INDEX idx_processor_dead_letter_record_indexed_at
    ON processor.processor_dead_letter_record (indexed_at DESC);

CREATE INDEX idx_processor_dead_letter_record_state_indexed_at
    ON processor.processor_dead_letter_record (state, indexed_at DESC);

CREATE INDEX idx_processor_dead_letter_record_dlt_topic_indexed_at
    ON processor.processor_dead_letter_record (dlt_topic, indexed_at DESC);

CREATE INDEX idx_processor_dead_letter_record_failure_kind_indexed_at
    ON processor.processor_dead_letter_record (failure_kind, indexed_at DESC);

CREATE INDEX idx_processor_dead_letter_record_stage_indexed_at
    ON processor.processor_dead_letter_record (stage, indexed_at DESC);

CREATE INDEX idx_processor_dead_letter_source_context_source_id
    ON processor.processor_dead_letter_source_context (source_id);

CREATE INDEX idx_processor_dead_letter_job_context_job_id
    ON processor.processor_dead_letter_job_context (job_id);

CREATE INDEX idx_processor_dead_letter_mapper_context_mapper_id
    ON processor.processor_dead_letter_mapper_context (mapper_id);

CREATE INDEX idx_processor_dead_letter_import_context_import_id
    ON processor.processor_dead_letter_import_context (import_id);
