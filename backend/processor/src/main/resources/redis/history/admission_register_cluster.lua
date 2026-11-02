-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_register_cluster
-- KEYS[1]: activeRequestKey
-- KEYS[2]: activeRunningKey
-- KEYS[3]: activeBacklogKey
-- KEYS[4]: replacementRequestKey
-- KEYS[5]: replacementJobsKey
-- KEYS[6]: jobMetaKey
-- KEYS[7]: queuedPayloadsHashKey
-- ARGV[1]: jobId
-- ARGV[2]: requestId
-- ARGV[3]: requestedAt
-- ARGV[4]: payloadJson
-- ARGV[5]: maxConcurrent
-- ARGV[6]: ttlMs
-- ARGV[7]: userId
-- Returns: [action, queueRole, replacementQueuedFlag, replacementSupersededFlag, replacementStartedFlag, cancelledJobIdsCsv, jobsToStartCsv]
local activeRequestKey = KEYS[1]
local activeRunningKey = KEYS[2]
local activeBacklogKey = KEYS[3]
local replacementRequestKey = KEYS[4]
local replacementJobsKey = KEYS[5]
local jobMetaKey = KEYS[6]
local payloadHashKey = KEYS[7]

local jobId = ARGV[1]
local requestId = ARGV[2]
local requestedAt = ARGV[3]
local payloadJson = ARGV[4]
local maxConcurrent = tonumber(ARGV[5])
local ttlMs = tonumber(ARGV[6])
local userId = ARGV[7]
local payloadField = jobId
local deadlineField = jobId .. ':expiresAt'
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
ttlMs = positive_integer(ARGV[6])
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
  or require_type(jobMetaKey, { 'none', 'hash' })
  or require_type(payloadHashKey, { 'none', 'hash' })
if typeError then
  return typeError
end

local controllerAbsent = redis.call('EXISTS', activeRequestKey) == 0
  and redis.call('EXISTS', activeRunningKey) == 0
  and redis.call('EXISTS', activeBacklogKey) == 0
  and redis.call('EXISTS', replacementRequestKey) == 0
  and redis.call('EXISTS', replacementJobsKey) == 0

local function store_payload()
  redis.call('HSET', payloadHashKey, payloadField, payloadJson, deadlineField, payloadExpiresAt)
  redis.call('PEXPIREAT', payloadHashKey, payloadExpiresAt)
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

redis.call('HSET', jobMetaKey, 'jobId', jobId, 'requestId', requestId, 'requestedAt', requestedAt, 'userId', userId)
redis.call('PEXPIRE', jobMetaKey, ttlMs)

local activeRequestId = redis.call('GET', activeRequestKey)
if not activeRequestId then
  if controllerAbsent then
    redis.call('DEL', payloadHashKey)
  end
  redis.call('SET', activeRequestKey, requestId, 'PX', ttlMs)
  redis.call('SADD', activeRunningKey, jobId)
  redis.call('PEXPIRE', activeRunningKey, ttlMs)
  return { 'START_NOW', '', '0', '0', '0', '', '' }
end

if activeRequestId == requestId then
  local runningCount = tonumber(redis.call('SCARD', activeRunningKey))
  if runningCount < maxConcurrent then
    redis.call('SADD', activeRunningKey, jobId)
    redis.call('PEXPIRE', activeRunningKey, ttlMs)
    return { 'START_NOW', '', '0', '0', '0', '', '' }
  end
  redis.call('RPUSH', activeBacklogKey, jobId)
  redis.call('PEXPIRE', activeBacklogKey, ttlMs)
  store_payload()
  return { 'QUEUE_ONLY', 'ACTIVE_REQUEST_BACKLOG', '0', '0', '0', '', '' }
end

local existingReplacementRequest = redis.call('HGET', replacementRequestKey, 'requestId')
if not existingReplacementRequest then
  local discarded = redis.call('LRANGE', replacementJobsKey, 0, -1)
  for i = 1, #discarded do
    delete_payload_fields(discarded[i])
  end
  redis.call('HSET', replacementRequestKey, 'requestId', requestId, 'requestedAt', requestedAt)
  redis.call('PEXPIRE', replacementRequestKey, ttlMs)
  redis.call('DEL', replacementJobsKey)
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  store_payload()
  return { 'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '1', '0', '0', '', '' }
end

if existingReplacementRequest == requestId then
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  store_payload()
  return { 'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '0', '0', '0', '', '' }
end

local existingRequestedAt = redis.call('HGET', replacementRequestKey, 'requestedAt')
if not existingRequestedAt then
  existingRequestedAt = ''
end
local existingRequestId = existingReplacementRequest

local incomingWins = false
if requestedAt > existingRequestedAt then
  incomingWins = true
elseif requestedAt == existingRequestedAt and requestId > existingRequestId then
  incomingWins = true
end

if incomingWins then
  local cancelled = redis.call('LRANGE', replacementJobsKey, 0, -1)
  for i = 1, #cancelled do
    delete_payload_fields(cancelled[i])
  end
  redis.call('DEL', replacementJobsKey)
  redis.call('HSET', replacementRequestKey, 'requestId', requestId, 'requestedAt', requestedAt)
  redis.call('PEXPIRE', replacementRequestKey, ttlMs)
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  store_payload()
  return { 'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '1', '1', '0', table.concat(cancelled, ','), '' }
end

delete_payload_fields(jobId)
return { 'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '0', '0', '0', jobId, '' }
