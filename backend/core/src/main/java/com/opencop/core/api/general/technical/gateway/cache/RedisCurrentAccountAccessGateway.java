// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.CacheOutcome;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.InvalidValueReason;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.MutationOutcome;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.MutationStage;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisCurrentAccountAccessGateway implements CurrentAccountAccessCacheGateway {

  private static final int CONTRACT_VERSION = 1;
  private static final String KEY_PREFIX =
      "opencop:auth:current-account:v" + CONTRACT_VERSION + ":";
  private static final List<RecordComponent> CACHE_VALUE_SCHEMA =
      List.of(CacheValue.class.getRecordComponents());

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final CurrentAccountCacheTelemetry telemetry;

  public RedisCurrentAccountAccessGateway(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${opencop.auth.current-account-cache.ttl:15m}") Duration ttl,
      CurrentAccountCacheTelemetry telemetry) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Current-account cache TTL must be positive");
    }
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
    this.telemetry = telemetry;
  }

  @Override
  public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
    try {
      var payload = redisTemplate.opsForValue().get(key(internalUserId));
      if (payload == null) {
        telemetry.recordCacheOutcome(CacheOutcome.MISS);
        return Optional.empty();
      }
      var lookup = toAccess(objectMapper.readTree(payload), internalUserId);
      if (lookup.access().isPresent()) {
        telemetry.recordCacheOutcome(CacheOutcome.HIT);
      } else {
        telemetry.recordInvalidValue(lookup.invalidReason().orElseThrow());
      }
      return lookup.access();
    } catch (JacksonException e) {
      telemetry.recordInvalidValue(InvalidValueReason.UNREADABLE_JSON, e);
      return Optional.empty();
    } catch (DataAccessException e) {
      telemetry.recordCacheFailure(CacheOutcome.READ_FAILURE, e);
      return Optional.empty();
    }
  }

  @Override
  public void populate(CurrentAccountAccess access) {
    try {
      var payload = objectMapper.writeValueAsString(CacheValue.from(access));
      redisTemplate.opsForValue().set(key(access.internalUserId()), payload, ttl);
    } catch (JacksonException | DataAccessException e) {
      telemetry.recordCacheFailure(CacheOutcome.POPULATE_FAILURE, e);
    }
  }

  @Override
  public void evict(UUID internalUserId) {
    evict(internalUserId, MutationStage.REQUIRED_PRECOMMIT_EVICT, MutationOutcome.ROLLED_BACK);
  }

  @Override
  public void scheduleEvictionAfterCommit(UUID internalUserId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            evictBestEffort(internalUserId);
          }
        });
  }

  public static String key(UUID internalUserId) {
    return KEY_PREFIX + internalUserId;
  }

  private void evictBestEffort(UUID internalUserId) {
    try {
      evict(
          internalUserId,
          MutationStage.BEST_EFFORT_POSTCOMMIT_EVICT,
          MutationOutcome.COMMITTED_REPAIR_FAILED);
    } catch (RuntimeException _) {
      // The committed mutation cannot be rolled back; telemetry records the failed repair.
    }
  }

  private void evict(UUID internalUserId, MutationStage stage, MutationOutcome failureOutcome) {
    try {
      redisTemplate.delete(key(internalUserId));
      telemetry.recordMutationSuccess(stage);
    } catch (RuntimeException exception) {
      telemetry.recordMutationFailure(stage, failureOutcome, exception);
      throw exception;
    }
  }

  private CacheLookup toAccess(JsonNode value, UUID expectedInternalUserId)
      throws JacksonException {
    if (value == null || !value.isObject()) {
      return CacheLookup.invalid(InvalidValueReason.NOT_OBJECT);
    }

    for (var field : CACHE_VALUE_SCHEMA) {
      if (value.get(field.getName()) == null) {
        return CacheLookup.invalid(InvalidValueReason.MISSING_FIELD);
      }
    }
    for (var field : CACHE_VALUE_SCHEMA) {
      if (!hasExpectedType(value.get(field.getName()), field.getType())) {
        return CacheLookup.invalid(InvalidValueReason.WRONG_TYPE);
      }
    }

    return objectMapper.treeToValue(value, CacheValue.class).toAccess(expectedInternalUserId);
  }

  private static boolean hasExpectedType(JsonNode value, Class<?> fieldType) {
    if (fieldType == int.class) {
      return value.isIntegralNumber() && value.canConvertToInt();
    }
    if (fieldType == String.class) {
      return value.isString();
    }
    if (fieldType == boolean.class) {
      return value.isBoolean();
    }
    if (fieldType == long.class) {
      return value.isIntegralNumber() && value.canConvertToLong();
    }
    throw new IllegalStateException(
        "Unsupported current-account cache field type: " + fieldType.getName());
  }

  private record CacheLookup(
      Optional<CurrentAccountAccess> access, Optional<InvalidValueReason> invalidReason) {

    private static CacheLookup hit(CurrentAccountAccess access) {
      return new CacheLookup(Optional.of(access), Optional.empty());
    }

    private static CacheLookup invalid(InvalidValueReason reason) {
      return new CacheLookup(Optional.empty(), Optional.of(reason));
    }
  }

  private record CacheValue(
      int contractVersion,
      String internalUserId,
      boolean active,
      boolean administrator,
      long sessionGeneration,
      long accountRevision) {

    private CacheLookup toAccess(UUID expectedInternalUserId) {
      if (contractVersion != CONTRACT_VERSION) {
        return CacheLookup.invalid(InvalidValueReason.UNSUPPORTED_VERSION);
      }
      if (!expectedInternalUserId.toString().equals(internalUserId)) {
        return CacheLookup.invalid(InvalidValueReason.USER_ID_MISMATCH);
      }
      if (sessionGeneration < 0 || accountRevision < 0) {
        return CacheLookup.invalid(InvalidValueReason.NEGATIVE_VALUE);
      }
      return CacheLookup.hit(
          new CurrentAccountAccess(
              expectedInternalUserId, active, administrator, sessionGeneration, accountRevision));
    }

    private static CacheValue from(CurrentAccountAccess access) {
      return new CacheValue(
          CONTRACT_VERSION,
          access.internalUserId().toString(),
          access.active(),
          access.administrator(),
          access.sessionGeneration(),
          access.accountRevision());
    }
  }
}
