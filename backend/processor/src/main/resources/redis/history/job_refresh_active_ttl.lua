-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: job_refresh_active_ttl
-- KEYS[1]: job hash key
-- ARGV[1]: updatedAt (ISO-8601 string)
-- ARGV[2]: expiresAt (ISO-8601 string)
-- ARGV[3]: ttlMillis
-- ARGV[4..N]: terminal status names (single source of truth: HistoricalJobStatus.TERMINAL)
-- Returns: 1 when refreshed, 0 when missing or status is terminal
--
-- Only updatedAt and expiresAt are written, so a concurrent guarded transition
-- (cancellation, failure, completion, timeout) cannot be clobbered by a stale
-- in-process snapshot. The terminal-status check is read-and-decide atomically
-- inside the script.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local currentStatus = redis.call('HGET', KEYS[1], 'status')
for i = 4, #ARGV do
  if currentStatus == ARGV[i] then
    return 0
  end
end
redis.call('HSET', KEYS[1], 'updatedAt', ARGV[1], 'expiresAt', ARGV[2])
redis.call('PEXPIRE', KEYS[1], ARGV[3])
return 1
