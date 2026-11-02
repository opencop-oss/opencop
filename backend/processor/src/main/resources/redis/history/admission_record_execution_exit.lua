-- Copyright 2026 NexTech Solutions LLC
-- SPDX-License-Identifier: Apache-2.0

-- Script: admission_record_execution_exit
-- KEYS[1]: activeRequestKey
-- KEYS[2]: activeRunningKey
-- KEYS[3]: activeBacklogKey
-- KEYS[4]: replacementRequestKey
-- KEYS[5]: replacementJobsKey
-- KEYS[6]: jobMetaKey
-- ARGV[1]: jobId
-- ARGV[2]: ttlMs
-- ARGV[3]: maxConcurrent
-- Returns: [action, queueRole, replacementQueuedFlag, replacementSupersededFlag, replacementStartedFlag, cancelledJobIdsCsv, jobsToStartCsv]
local activeRequestKey = KEYS[1]
local activeRunningKey = KEYS[2]
local activeBacklogKey = KEYS[3]
local replacementRequestKey = KEYS[4]
local replacementJobsKey = KEYS[5]
local jobMetaKey = KEYS[6]

local jobId = ARGV[1]
local ttlMs = tonumber(ARGV[2])
local maxConcurrent = tonumber(ARGV[3])

local requestId = redis.call('HGET', jobMetaKey, 'requestId')
if not requestId then
  return {'NOOP', '', '0', '0', '0', '', ''}
end
local activeRequestId = redis.call('GET', activeRequestKey)
if not activeRequestId or activeRequestId ~= requestId then
  return {'NOOP', '', '0', '0', '0', '', ''}
end
local removed = redis.call('SREM', activeRunningKey, jobId)
if removed == 0 then
  return {'NOOP', '', '0', '0', '0', '', ''}
end
local nextBacklog = redis.call('LPOP', activeBacklogKey)
if nextBacklog then
  redis.call('SADD', activeRunningKey, nextBacklog)
  redis.call('PEXPIRE', activeRunningKey, ttlMs)
  return {'NOOP', '', '0', '0', '0', '', nextBacklog}
end

local runningCount = tonumber(redis.call('SCARD', activeRunningKey))
if runningCount > 0 then
  return {'NOOP', '', '0', '0', '0', '', ''}
end

local replacementRequest = redis.call('HGET', replacementRequestKey, 'requestId')
if not replacementRequest then
  redis.call('DEL', activeRequestKey)
  return {'NOOP', '', '0', '0', '0', '', ''}
end

local startJobs = redis.call('LRANGE', replacementJobsKey, 0, maxConcurrent - 1)
local remainingJobs = redis.call('LRANGE', replacementJobsKey, maxConcurrent, -1)
redis.call('SET', activeRequestKey, replacementRequest, 'PX', ttlMs)
redis.call('DEL', replacementRequestKey)
redis.call('DEL', replacementJobsKey)
redis.call('DEL', activeBacklogKey)
for i=1,#startJobs do
  redis.call('SADD', activeRunningKey, startJobs[i])
end
for i=1,#remainingJobs do
  redis.call('RPUSH', activeBacklogKey, remainingJobs[i])
end
redis.call('PEXPIRE', activeBacklogKey, ttlMs)
redis.call('PEXPIRE', activeRunningKey, ttlMs)
return {'NOOP', '', '0', '0', '1', '', table.concat(startJobs, ',')}
