-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_cleanup_state
-- KEYS[1]: activeRequestKey
-- KEYS[2]: activeRunningKey
-- KEYS[3]: activeBacklogKey
-- KEYS[4]: replacementRequestKey
-- KEYS[5]: replacementJobsKey
-- KEYS[6]: payloadKey
-- KEYS[7]: jobMetaKey
-- ARGV[1]: jobId
-- ARGV[2]: ttlMs
-- ARGV[3]: maxConcurrent
-- Returns: promoted job IDs CSV, or empty string when none is promotable
local activeRequestKey = KEYS[1]
local activeRunningKey = KEYS[2]
local activeBacklogKey = KEYS[3]
local replacementRequestKey = KEYS[4]
local replacementJobsKey = KEYS[5]
local payloadKey = KEYS[6]
local jobMetaKey = KEYS[7]
local jobId = ARGV[1]
local ttlMs = tonumber(ARGV[2])
local maxConcurrent = tonumber(ARGV[3])

redis.call('SREM', activeRunningKey, jobId)
redis.call('LREM', activeBacklogKey, 0, jobId)
redis.call('LREM', replacementJobsKey, 0, jobId)
local replacementCount = tonumber(redis.call('LLEN', replacementJobsKey))
if replacementCount == 0 then
  redis.call('DEL', replacementRequestKey)
end
redis.call('DEL', payloadKey)
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
for i=1,#startJobs do
  redis.call('SADD', activeRunningKey, startJobs[i])
end
for i=1,#remainingJobs do
  redis.call('RPUSH', activeBacklogKey, remainingJobs[i])
end
redis.call('DEL', replacementRequestKey)
redis.call('PEXPIRE', activeBacklogKey, ttlMs)
redis.call('PEXPIRE', activeRunningKey, ttlMs)
return table.concat(startJobs, ',')
