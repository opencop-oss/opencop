-- A listening data source binds a local socket, so at most one active source may hold a port.
-- That socket is per Connect process, but this rule is deployment-wide on purpose: assignment
-- moves sources between instances, so a per-instance key would break a working source on
-- failover, and a per-tenant key would still be wrong — two tenants cannot share a host port.
-- provider is in the key, not only the predicate: exclusivity is per protocol, so adding a
-- listening transport later widens the predicate without wrongly colliding across protocols.
-- enabled is deliberately excluded — a stopped source keeps its port, and setEnabled stays a
-- plain UPDATE that cannot violate a constraint. Only archiving releases the port.
CREATE UNIQUE INDEX uk_data_source_listen_port_active
    ON profiles.data_source (provider, port)
    WHERE provider = 'UDP'::profiles.transport_kind
      AND archived_at IS NULL;

-- Endpoint collisions are permitted for every transport, so the (host, port) lookup that rejected
-- them is gone and this index has no remaining reader.
-- IF EXISTS because a baselined or hand-tuned environment may not carry the index at all, and
-- failing startup over an index this migration wants gone would be a failure about nothing.
DROP INDEX IF EXISTS profiles.ds_host_port_idx;
