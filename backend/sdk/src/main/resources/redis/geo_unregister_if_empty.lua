-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: geo_unregister_if_empty
-- KEYS[1]: source registry marker
-- KEYS[2]: source update-time sorted set
-- KEYS[3]: source stale-time sorted set
-- Returns: 0 when either live index is non-empty, otherwise the DEL result for KEYS[1]
--
-- Every key belongs to one geo source and therefore shares one hash tag, so this
-- script stays inside a single Redis Cluster slot. A source that still has live
-- index members must keep its marker, otherwise a writer that repopulated the
-- source concurrently would become undiscoverable.
if redis.call('ZCARD', KEYS[2]) ~= 0 then
  return 0
end

if redis.call('ZCARD', KEYS[3]) ~= 0 then
  return 0
end

return redis.call('DEL', KEYS[1])
