-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Atomically evict a cached event if no newer version has been written.
--
-- KEYS:
-- 1 updatedAtZset  -- sorted set keyed by event update time
-- 2 staleAtZset    -- sorted set keyed by event stale time
-- 3 eventKey       -- actual Redis key holding the event payload
--
-- ARGV:
-- 1 ifNotNewerThanMs  -- evict only if stored score <= this value
--
-- Returns:
--  0  event was not in Redis (already gone)
-- -1  a newer replacement exists (score > threshold); eviction skipped
--  1  event evicted successfully

local updatedAtKey = KEYS[1]
local staleAtKey   = KEYS[2]
local eventKey     = KEYS[3]
local threshold    = tonumber(ARGV[1])

local score = redis.call('ZSCORE', updatedAtKey, eventKey)
if not score then
    return 0
end
if tonumber(score) > threshold then
    return -1
end
redis.call('DEL', eventKey)
redis.call('ZREM', updatedAtKey, eventKey)
redis.call('ZREM', staleAtKey, eventKey)
return 1
