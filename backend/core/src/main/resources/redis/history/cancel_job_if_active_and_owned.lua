-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: cancel_job_if_active_and_owned
-- KEYS[1]: Historical job hash key
-- ARGV[1]: requestedBy field name
-- ARGV[2]: requestedBy value
-- ARGV[3]: status field name
-- ARGV[4]: completed status value
-- ARGV[5]: failed status value
-- ARGV[6]: cancelled status value
-- ARGV[7]: requestId field name
-- ARGV[8]: replacement requestId value
-- ARGV[9]: stage field name
-- ARGV[10]: cancellation requested stage value
-- ARGV[11]: failureReason field name
-- ARGV[12]: failureReason value
-- ARGV[13]: updatedAt field name
-- ARGV[14]: updatedAt value
-- Returns: 1 when updated, 0 when unchanged
local jobOwner = redis.call('HGET', KEYS[1], ARGV[1])
if (not jobOwner) or jobOwner ~= ARGV[2] then
  return 0
end
local status = redis.call('HGET', KEYS[1], ARGV[3])
if (not status) or status == ARGV[4] or status == ARGV[5] or status == ARGV[6] then
  return 0
end
local requestId = redis.call('HGET', KEYS[1], ARGV[7])
if requestId and requestId == ARGV[8] then
  return 0
end
redis.call('HSET', KEYS[1], ARGV[9], ARGV[10], ARGV[11], ARGV[12], ARGV[13], ARGV[14])
return 1
