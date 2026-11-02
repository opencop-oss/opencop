-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: chunk_mark_delivery_terminal
-- KEYS[1]: chunk hash key
-- ARGV[1]: allowedChunkKeyPrefix
-- ARGV[2]: required current status
-- ARGV[3]: target terminal status
-- ARGV[4]: failureReason
-- ARGV[5]: updatedAt
-- ARGV[6]: expiresAt
-- ARGV[7]: ttlMs
-- Returns: 1 when state transitions, 0 otherwise
local STATUS_FIELD = 'status'
local FAILURE_REASON_FIELD = 'failureReason'
local UPDATED_AT_FIELD = 'updatedAt'
local EXPIRES_AT_FIELD = 'expiresAt'

if string.sub(KEYS[1], 1, string.len(ARGV[1])) ~= ARGV[1] then
  return 0
end

if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end

local currentStatus = redis.call('HGET', KEYS[1], STATUS_FIELD)
if currentStatus ~= ARGV[2] then
  return 0
end

redis.call(
  'HSET',
  KEYS[1],
  STATUS_FIELD,
  ARGV[3],
  FAILURE_REASON_FIELD,
  ARGV[4],
  UPDATED_AT_FIELD,
  ARGV[5],
  EXPIRES_AT_FIELD,
  ARGV[6])
redis.call('PEXPIRE', KEYS[1], ARGV[7])
return 1
