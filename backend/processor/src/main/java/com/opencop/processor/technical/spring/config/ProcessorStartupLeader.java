// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.core.sdk.helper.InstanceIdentity;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * One-shot startup leader election: leadership is decided once at construction and never renewed,
 * released, or re-elected. The lock TTL is the crash-reclaim window — a replacement instance
 * starting inside a dead leader's TTL stays non-leader until its own next restart. Redis failure
 * fails open (proceeds as leader) because the guarded work — bounded-batch retention deletes — is
 * idempotent: dual leaders waste work but cannot corrupt state.
 */
public class ProcessorStartupLeader {

  public static final String DEFAULT_LOCK_KEY = "opencop:processor:startup-leader";
  private static final Duration LOCK_TTL = Duration.ofMinutes(5);
  private static final Logger log = LoggerFactory.getLogger(ProcessorStartupLeader.class);

  private final boolean leader;

  public ProcessorStartupLeader(
      StringRedisTemplate redisTemplate, String lockKey, boolean electionEnabled) {
    var instanceId = InstanceIdentity.resolveInstanceId("processor");
    if (!electionEnabled) {
      leader = true;
      log.info("Processor leader election disabled, assuming leader (instance: {})", instanceId);
      return;
    }
    boolean acquired;
    try {
      acquired =
          Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(lockKey, instanceId, LOCK_TTL));
    } catch (Exception exception) {
      acquired = true;
      log.warn(
          "Redis unavailable for Processor leader election, proceeding as leader: {}",
          exception.getMessage());
    }
    leader = acquired;
    log.info("Processor startup leader: {} (instance: {})", leader, instanceId);
  }

  public boolean isLeader() {
    return leader;
  }
}
