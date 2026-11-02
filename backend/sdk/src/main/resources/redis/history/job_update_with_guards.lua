-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: job_update_with_guards
-- KEYS[1]: job hash key
-- ARGV layout: blockedStatusCount, blocked statuses, blockedStageCount, blocked stages, ttlMillis, repeated field/value pairs
-- Returns: 1 when updated, 0 when missing or blocked by status/stage guards
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local index = 1
local blockedStatusCount = tonumber(ARGV[index])
index = index + 1
local currentStatus = redis.call('HGET', KEYS[1], 'status')
for i = 1, blockedStatusCount do
  if currentStatus == ARGV[index] then
    return 0
  end
  index = index + 1
end
local blockedStageCount = tonumber(ARGV[index])
index = index + 1
local currentStage = redis.call('HGET', KEYS[1], 'stage')
for i = 1, blockedStageCount do
  if currentStage == ARGV[index] then
    return 0
  end
  index = index + 1
end
local ttlMillis = ARGV[index]
index = index + 1
redis.call('HSET', KEYS[1], unpack(ARGV, index, #ARGV))
if ttlMillis ~= '' then
  redis.call('PEXPIRE', KEYS[1], ttlMillis)
end
return 1
