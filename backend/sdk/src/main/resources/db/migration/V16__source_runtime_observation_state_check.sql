-- This migration updates the CHECK constraint on profiles.source_runtime_observation.state.
-- RTSP was added to profiles.transport_kind in V15__add_rtsp_transport_kind.sql.
ALTER TABLE profiles.source_runtime_observation
    DROP CONSTRAINT source_runtime_observation_state_check;

ALTER TABLE profiles.source_runtime_observation
    ADD CONSTRAINT source_runtime_observation_state_check
        CHECK (state IN (
            'NEW',
            'CONNECTING',
            'CONNECTED',
            'DISCONNECTED',
            'STOPPED',
            'INCOMPATIBLE',
            'FAILED',
            'UNKNOWN'
        ));
