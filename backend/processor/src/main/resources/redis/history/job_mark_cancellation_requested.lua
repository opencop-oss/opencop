-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: job_mark_cancellation_requested
-- KEYS[1]: job hash key
-- ARGV layout: blockedStatusCount, blocked statuses, cancellationRequestedStage, failureReason, updatedAt, expiresAt, ttlMillis
-- Returns: 1 when updated or already cancellation-requested, 0 when missing or blocked by terminal status
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local currentStatus = redis.call('HGET', KEYS[1], 'status')
local blockedStatusCount = tonumber(ARGV[1])
for i = 1, blockedStatusCount do
  if currentStatus == ARGV[1 + i] then
    return 0
  end
end
local currentStage = redis.call('HGET', KEYS[1], 'stage')
local cancellationRequestedStage = ARGV[2 + blockedStatusCount]
if currentStage == cancellationRequestedStage then
  return 1
end
redis.call('HSET', KEYS[1], 'stage', cancellationRequestedStage)
redis.call('HSET', KEYS[1], 'failureReason', ARGV[3 + blockedStatusCount])
redis.call('HSET', KEYS[1], 'updatedAt', ARGV[4 + blockedStatusCount])
redis.call('HSET', KEYS[1], 'expiresAt', ARGV[5 + blockedStatusCount])
redis.call('PEXPIRE', KEYS[1], ARGV[6 + blockedStatusCount])
return 1
