-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_register
-- KEYS[1]: activeRequestKey (string, opencop:history:admission:user:{userId}:active-request)
-- KEYS[2]: activeRunningKey (set, opencop:history:admission:user:{userId}:active-running)
-- KEYS[3]: activeBacklogKey (list, opencop:history:admission:user:{userId}:active-backlog)
-- KEYS[4]: replacementRequestKey (hash, opencop:history:admission:user:{userId}:replacement-request)
-- KEYS[5]: replacementJobsKey (list, opencop:history:admission:user:{userId}:replacement-jobs)
-- KEYS[6]: jobMetaKey (hash, opencop:history:admission:job-meta:{jobId})
-- KEYS[7]: payloadKey (string, opencop:history:job-payload:{jobId})
-- ARGV[1]: jobId
-- ARGV[2]: requestId
-- ARGV[3]: requestedAt (zero-padded epochSecond:nano ordering token)
-- ARGV[4]: payloadJson
-- ARGV[5]: maxConcurrent
-- ARGV[6]: ttlMs
-- Returns: [action, queueRole, replacementQueuedFlag, replacementSupersededFlag, replacementStartedFlag, cancelledJobIdsCsv, jobsToStartCsv]
local activeRequestKey = KEYS[1]
local activeRunningKey = KEYS[2]
local activeBacklogKey = KEYS[3]
local replacementRequestKey = KEYS[4]
local replacementJobsKey = KEYS[5]
local jobMetaKey = KEYS[6]
local payloadKey = KEYS[7]

local jobId = ARGV[1]
local requestId = ARGV[2]
local requestedAt = ARGV[3]
local payloadJson = ARGV[4]
local maxConcurrent = tonumber(ARGV[5])
local ttlMs = tonumber(ARGV[6])

redis.call('HSET', jobMetaKey, 'jobId', jobId, 'requestId', requestId, 'requestedAt', requestedAt)
redis.call('PEXPIRE', jobMetaKey, ttlMs)

local activeRequestId = redis.call('GET', activeRequestKey)
if not activeRequestId then
  redis.call('SET', activeRequestKey, requestId, 'PX', ttlMs)
  redis.call('SADD', activeRunningKey, jobId)
  redis.call('PEXPIRE', activeRunningKey, ttlMs)
  return {'START_NOW', '', '0', '0', '0', '', ''}
end

if activeRequestId == requestId then
  local runningCount = tonumber(redis.call('SCARD', activeRunningKey))
  if runningCount < maxConcurrent then
    redis.call('SADD', activeRunningKey, jobId)
    redis.call('PEXPIRE', activeRunningKey, ttlMs)
    return {'START_NOW', '', '0', '0', '0', '', ''}
  end
  redis.call('RPUSH', activeBacklogKey, jobId)
  redis.call('PEXPIRE', activeBacklogKey, ttlMs)
  redis.call('SET', payloadKey, payloadJson, 'PX', ttlMs)
  return {'QUEUE_ONLY', 'ACTIVE_REQUEST_BACKLOG', '0', '0', '0', '', ''}
end

local existingReplacementRequest = redis.call('HGET', replacementRequestKey, 'requestId')
if not existingReplacementRequest then
  redis.call('HSET', replacementRequestKey, 'requestId', requestId, 'requestedAt', requestedAt)
  redis.call('PEXPIRE', replacementRequestKey, ttlMs)
  redis.call('DEL', replacementJobsKey)
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  redis.call('SET', payloadKey, payloadJson, 'PX', ttlMs)
  return {'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '1', '0', '0', '', ''}
end

if existingReplacementRequest == requestId then
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  redis.call('SET', payloadKey, payloadJson, 'PX', ttlMs)
  return {'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '0', '0', '0', '', ''}
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
  for i=1,#cancelled do
    redis.call('DEL', string.format('opencop:history:job-payload:%s', cancelled[i]))
  end
  redis.call('DEL', replacementJobsKey)
  redis.call('HSET', replacementRequestKey, 'requestId', requestId, 'requestedAt', requestedAt)
  redis.call('PEXPIRE', replacementRequestKey, ttlMs)
  redis.call('RPUSH', replacementJobsKey, jobId)
  redis.call('PEXPIRE', replacementJobsKey, ttlMs)
  redis.call('SET', payloadKey, payloadJson, 'PX', ttlMs)
  return {'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '1', '1', '0', table.concat(cancelled, ','), ''}
end

redis.call('DEL', payloadKey)
return {'QUEUE_ONLY', 'QUEUED_REPLACEMENT', '0', '0', '0', jobId, ''}
