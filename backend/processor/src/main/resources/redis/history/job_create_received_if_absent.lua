-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: job_create_received_if_absent
-- KEYS[1]: job hash key
-- KEYS[2]: user jobs sorted-set key
-- KEYS[3]: user request jobs sorted-set key
-- KEYS[4]: user request index sorted-set key
-- KEYS[5]: user job sequence key
-- KEYS[6]: user request sequence key
-- ARGV layout: repeated field/value pairs, then ttlMillis, jobId, requestId
-- Returns: 1 when created, 0 when job hash already exists
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
local ttlMillis = ARGV[#ARGV - 2]
local jobId = ARGV[#ARGV - 1]
local requestId = ARGV[#ARGV]
local userSequence = redis.call('INCR', KEYS[5])
local requestSequence = redis.call('INCR', KEYS[6])
redis.call('HSET', KEYS[1], unpack(ARGV, 1, #ARGV - 3))
redis.call('HSET', KEYS[1], 'userSequence', tostring(userSequence), 'requestSequence', tostring(requestSequence))
redis.call('ZADD', KEYS[2], userSequence, jobId)
redis.call('ZADD', KEYS[3], requestSequence, jobId)
redis.call('ZADD', KEYS[4], requestSequence, requestId)
redis.call('PEXPIRE', KEYS[1], ttlMillis)
redis.call('PEXPIRE', KEYS[2], ttlMillis)
redis.call('PEXPIRE', KEYS[3], ttlMillis)
redis.call('PEXPIRE', KEYS[4], ttlMillis)
redis.call('PEXPIRE', KEYS[5], ttlMillis)
redis.call('PEXPIRE', KEYS[6], ttlMillis)
return 1
