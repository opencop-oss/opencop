// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.sdk.helper.InstanceIdentity;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Determines whether this Core instance is the startup leader via Redis SETNX.
 *
 * <p>Only the leader runs startup operations (auto-start data sources, BYOD reconciliation) to
 * prevent duplicate work when running multiple Core instances. The lock key has a 5-minute TTL —
 * generous enough for all startup operations including per-source retries with backoff.
 *
 * <p>The key is NOT deleted after startup — the TTL prevents a late-starting Core instance from
 * also claiming leadership during the same boot cycle.
 *
 * <p>Fail-open: if Redis is unavailable, this instance proceeds as leader. The common case is N=1,
 * and failing startup entirely is too aggressive.
 *
 * <p>In a future K8s deployment, prefer a Kubernetes Lease for this startup leadership
 * coordination. The durable desired-state and reconciliation model remains the same; the Lease
 * would replace only this Redis SETNX mechanism.
 */
@Component
public class StartupLeader {

  private static final Logger log = LoggerFactory.getLogger(StartupLeader.class);
  private static final String DEFAULT_LOCK_KEY = "opencop:core:startup-leader";

  private final boolean leader;

  public StartupLeader(
      StringRedisTemplate redisTemplate,
      @Value("${opencop.core.startup-leader.lock-key:" + DEFAULT_LOCK_KEY + "}") String lockKey,
      @Value("${opencop.core.startup-leader.enabled:false}") boolean electionEnabled) {
    String instanceId = InstanceIdentity.resolveInstanceId("core");
    if (!electionEnabled) {
      this.leader = true;
      log.info("Leader election disabled, assuming leader (instance: {})", instanceId);
      return;
    }
    boolean acquired;
    try {
      acquired =
          Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(lockKey, instanceId, Duration.ofMinutes(5)));
    } catch (Exception e) {
      acquired = true;
      log.warn(
          "Redis unavailable for startup leader election, proceeding as leader: {}",
          e.getMessage());
    }
    this.leader = acquired;
    log.info("Startup leader: {} (instance: {})", leader, instanceId);
  }

  public boolean isLeader() {
    return leader;
  }
}
