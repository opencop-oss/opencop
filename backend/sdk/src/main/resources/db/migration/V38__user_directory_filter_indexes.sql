-- Indexes backing the admin Users grid's free-text search and per-column filters.
--
-- The user directory read is a UNION ALL of opencop_user and iam.user_group, ordered by
-- name/type/id with LIMIT/OFFSET. Search matches substrings (LIKE '%term%'), which no btree can
-- serve because of the leading wildcard.
--
-- NO TRIGRAM INDEXES, DELIBERATELY. An earlier revision created the pg_trgm extension and GIN
-- gin_trgm_ops indexes on the searchable columns. Measured at production scale (6,005 users,
-- 51 groups) they did not earn their keep:
--
--   * The search box ORs LIKE across five attributes, and the planner will not assemble a BitmapOr
--     of five GIN scans at this size. It chose a Seq Scan every time, even for a term matching a
--     single row; forcing enable_seqscan=off made it slower (8.8ms) via an unrelated btree.
--     Search measured 6.0ms with the trigram indexes present and 5.0ms without them.
--   * A single-column filter chip DID use a trigram index (0.1ms vs 1.8ms). Real, but a 1.7ms
--     saving sits behind a 300ms search debounce and a network round trip.
--   * count(*) over the union, which the page total needs, is 0.9ms and cannot use a trigram index
--     regardless. It is the real scaling ceiling, tracked separately as keyset pagination.
--
-- Dropping them also removes a deployment dependency: extensions are not available by default on
-- managed Postgres. Azure Flexible Server requires allowlisting via the azure.extensions server
-- parameter, which this project has already had to do for PGCRYPTO and POSTGIS. Substring search is
-- unaffected either way -- LIKE '%term%' returns identical rows with or without an index, so this
-- was only ever an optimization. Revisit with EXPLAIN if the user directory grows by orders of
-- magnitude.
--
-- Other known limits, accepted for now:
--   * notContains / notEquals / isEmpty / isNotEmpty are full-branch scans by nature.
--   * No index is needed for the `type` filter: it is a predicate on a constant literal inside
--     each UNION ALL branch, so Postgres eliminates the other branch at plan time.

-- createdAt before / after / between.
CREATE INDEX idx_opencop_user_created_at ON opencop_user (created_at) WHERE deleted_at IS NULL;

-- "Role = administrator" is highly selective in the TRUE direction, and this partial index is
-- confirmed used for that filter. The FALSE direction is the majority of rows, where a scan is
-- correct. The (username, id) key is carried on the chance it also helps the ordering, but see the
-- note below -- the planner was not observed using that column pair to avoid the sort.
CREATE INDEX idx_opencop_user_administrator_true ON opencop_user (username, id)
    WHERE administrator AND deleted_at IS NULL;

-- No ordering index on the user branch: measured against a real Postgres at 3k and 200k rows, the
-- planner would not use `(username, id) WHERE deleted_at IS NULL` for the default
-- ORDER BY name, entry_type, id -- it chose Seq Scan + Sort every time, even with enable_seqscan
-- off. The group branch's equivalent below IS selected (Index Scan feeding a Merge Append), so the
-- asymmetry appears to come from how the planner handles the WHERE-bearing UNION ALL branch rather
-- than from the index shape. An index nothing reads is pure write cost, so it is omitted.

-- Group side of the union. Unlike the user branch, this ordering index IS selected by the planner.
CREATE INDEX idx_user_group_directory_order ON iam.user_group (name, id);
CREATE INDEX idx_user_group_created_at      ON iam.user_group (created_at);
