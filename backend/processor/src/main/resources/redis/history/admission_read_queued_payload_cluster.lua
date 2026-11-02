-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_read_queued_payload_cluster
-- KEYS[1]: queuedPayloadsHashKey; ARGV[1]: jobId
-- Returns: payload JSON when its paired deadline is valid and unexpired; false (Java null) otherwise.
local MAX_EXACT_INTEGER = 9007199254740991
local function positive_integer(value)
  if type(value) ~= 'string' or not string.match(value, '^%d+$') then return nil end
  local number = tonumber(value)
  if not number or number <= 0 or number > MAX_EXACT_INTEGER
      or number ~= math.floor(number) then return nil end
  return number
end
local function server_millis()
  local clock = redis.call('TIME')
  return tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
end
local fields = redis.call('HMGET', KEYS[1], ARGV[1], ARGV[1] .. ':expiresAt')
if not fields[1] or not fields[2] then return false end
local deadline = positive_integer(fields[2])
if not deadline or deadline <= server_millis() then return false end
return fields[1]
