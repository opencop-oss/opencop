-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- KEYS:
-- 1 eventKey
-- 2 updatedAtZset
-- 3 staleAtZset
-- 4 source registry (standalone global ZSET or cluster per-source marker)

-- ARGV:
-- 1 payload (binary)
-- 2 ttlMs
-- 3 eventTime
-- 4 staleAt
-- 5 nowMs
-- 6 sourceMember
-- 7 staleKeepMs
-- 8 registryKind

local eventKey     = KEYS[1]
local updatedAtKey = KEYS[2]
local staleAtKey   = KEYS[3]
local registryKey  = KEYS[4]

local payload      = ARGV[1]
local ttlMs        = tonumber(ARGV[2])
local eventTime    = tonumber(ARGV[3])
local staleAt      = tonumber(ARGV[4])
local nowMs        = tonumber(ARGV[5])
local sourceMember = ARGV[6]
local staleKeepMs  = tonumber(ARGV[7])
local registryKind = ARGV[8]

if registryKind ~= 'GLOBAL_SORTED_SET' and registryKind ~= 'PER_SOURCE_MARKER' then
	return redis.error_reply('ERR invalid live-geo registry kind')
end

if registryKind == 'PER_SOURCE_MARKER' then
	redis.call('SET', registryKey, sourceMember)
end

if ttlMs == nil or ttlMs <= 0 then
-- keep stale event briefly so WS can broadcast it
	redis.call('PSETEX', eventKey, staleKeepMs, payload)
	redis.call('ZREM', updatedAtKey, eventKey)
	redis.call('ZREM', staleAtKey, eventKey)
else
	local last = redis.call('ZSCORE', updatedAtKey, eventKey)
	if (not last) or tonumber(last) < eventTime then
		redis.call('PSETEX', eventKey, ttlMs, payload)
		redis.call('ZADD', updatedAtKey, eventTime, eventKey)
		redis.call('ZADD', staleAtKey, staleAt, eventKey)
	end
end

if registryKind == 'GLOBAL_SORTED_SET' then
	-- track sources (with "last update time")
	redis.call('ZADD', registryKey, nowMs, sourceMember)
end

return 1
