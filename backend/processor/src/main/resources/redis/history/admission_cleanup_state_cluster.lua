-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_cleanup_state_cluster
-- KEYS[1]: activeRequestKey
-- KEYS[2]: activeRunningKey
-- KEYS[3]: activeBacklogKey
-- KEYS[4]: replacementRequestKey
-- KEYS[5]: replacementJobsKey
-- KEYS[6]: queuedPayloadsHashKey
-- KEYS[7]: jobMetaKey
-- ARGV[1]: jobId
-- ARGV[2]: ttlMs
-- ARGV[3]: maxConcurrent
-- Returns: comma-separated promoted job IDs, or an empty string when no job is promoted.
local activeRequestKey = KEYS[1]
local activeRunningKey = KEYS[2]
local activeBacklogKey = KEYS[3]
local replacementRequestKey = KEYS[4]
local replacementJobsKey = KEYS[5]
local payloadHashKey = KEYS[6]
local jobMetaKey = KEYS[7]
local jobId = ARGV[1]
local ttlMs = tonumber(ARGV[2])
local maxConcurrent = tonumber(ARGV[3])
local deadlineSuffix = ':expiresAt'

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
ttlMs = positive_integer(ARGV[2])
if not ttlMs then return redis.error_reply('ERR invalid admission TTL') end
local nowMillis = server_millis()
if ttlMs > MAX_EXACT_INTEGER - nowMillis then
  return redis.error_reply('ERR admission deadline out of range')
end
local payloadExpiresAt = nowMillis + ttlMs

local function redis_type(key)
  local current = redis.call('TYPE', key)
  if type(current) == 'table' then
    return current.ok
  end
  return current
end

local function require_type(key, allowed)
  local actual = redis_type(key)
  for i = 1, #allowed do
    if actual == allowed[i] then
      return nil
    end
  end
  return redis.error_reply(
    'WRONGTYPE key=' .. key .. ' expected=' .. table.concat(allowed, '|') .. ' actual=' .. actual
  )
end

local typeError = require_type(activeRequestKey, { 'none', 'string' })
  or require_type(activeRunningKey, { 'none', 'set' })
  or require_type(activeBacklogKey, { 'none', 'list' })
  or require_type(replacementRequestKey, { 'none', 'hash' })
  or require_type(replacementJobsKey, { 'none', 'list' })
  or require_type(payloadHashKey, { 'none', 'hash' })
  or require_type(jobMetaKey, { 'none', 'hash' })
if typeError then
  return typeError
end

local function delete_payload_fields(targetJobId)
  redis.call('HDEL', payloadHashKey, targetJobId, targetJobId .. ':expiresAt')
end

local function prune_unusable_payload_fields()
  local cursor = '0'
  local expiredJobIds = {}
  repeat
    local result = redis.call('HSCAN', payloadHashKey, cursor, 'MATCH', '*:expiresAt', 'COUNT', 100)
    cursor = result[1]
    local entries = result[2]
    for i = 1, #entries, 2 do
      local field = entries[i]
      local deadline = positive_integer(entries[i + 1])
      if string.sub(field, -string.len(deadlineSuffix)) == deadlineSuffix then
        local targetJobId = string.sub(field, 1, string.len(field) - string.len(deadlineSuffix))
        if targetJobId ~= '' and (not deadline or deadline <= nowMillis) then
          table.insert(expiredJobIds, targetJobId)
        end
      end
    end
  until cursor == '0'
  for i = 1, #expiredJobIds do
    delete_payload_fields(expiredJobIds[i])
  end
end

prune_unusable_payload_fields()

redis.call('SREM', activeRunningKey, jobId)
redis.call('LREM', activeBacklogKey, 0, jobId)
redis.call('LREM', replacementJobsKey, 0, jobId)
local replacementCount = tonumber(redis.call('LLEN', replacementJobsKey))
if replacementCount == 0 then
  redis.call('DEL', replacementRequestKey)
end
delete_payload_fields(jobId)
if redis.call('HLEN', payloadHashKey) == 0 then
  redis.call('DEL', payloadHashKey)
else
  redis.call('PEXPIREAT', payloadHashKey, payloadExpiresAt)
end
redis.call('DEL', jobMetaKey)

local activeRequestId = redis.call('GET', activeRequestKey)
if not activeRequestId then
  return ''
end

local runningCount = tonumber(redis.call('SCARD', activeRunningKey))
if runningCount > 0 then
  redis.call('PEXPIRE', activeRunningKey, ttlMs)
  redis.call('PEXPIRE', activeBacklogKey, ttlMs)
  return ''
end

local nextBacklog = redis.call('LPOP', activeBacklogKey)
if nextBacklog then
  redis.call('SADD', activeRunningKey, nextBacklog)
  redis.call('PEXPIRE', activeRunningKey, ttlMs)
  redis.call('PEXPIRE', activeBacklogKey, ttlMs)
  return nextBacklog
end

local replacementRequest = redis.call('HGET', replacementRequestKey, 'requestId')
if not replacementRequest then
  redis.call('DEL', activeRequestKey)
  return ''
end

local startJobs = redis.call('LRANGE', replacementJobsKey, 0, maxConcurrent - 1)
if #startJobs == 0 then
  redis.call('DEL', replacementRequestKey)
  redis.call('DEL', activeRequestKey)
  return ''
end
local remainingJobs = redis.call('LRANGE', replacementJobsKey, maxConcurrent, -1)

redis.call('SET', activeRequestKey, replacementRequest, 'PX', ttlMs)
redis.call('DEL', replacementJobsKey)
redis.call('DEL', activeBacklogKey)
for i = 1, #startJobs do
  redis.call('SADD', activeRunningKey, startJobs[i])
end
for i = 1, #remainingJobs do
  redis.call('RPUSH', activeBacklogKey, remainingJobs[i])
end
redis.call('DEL', replacementRequestKey)
redis.call('PEXPIRE', activeBacklogKey, ttlMs)
redis.call('PEXPIRE', activeRunningKey, ttlMs)
return table.concat(startJobs, ',')
