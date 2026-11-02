CREATE TYPE processor.layer_merge_operation_phase AS ENUM (
    'PENDING',
    'MATERIALIZED',
    'HYDRATED',
    'FINALIZED',
    'FAILED'
);

CREATE TABLE processor.layer_merge_operation (
    target_layer_id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE,
    source_layer_ids UUID[] NOT NULL,
    phase processor.layer_merge_operation_phase NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    failure_reason TEXT,
    CONSTRAINT ck_layer_merge_operation_source_layer_ids_minimum_count
        CHECK (cardinality(source_layer_ids) >= 2)
);

CREATE INDEX idx_layer_merge_operation_phase_updated_at
    ON processor.layer_merge_operation (phase, updated_at DESC);
