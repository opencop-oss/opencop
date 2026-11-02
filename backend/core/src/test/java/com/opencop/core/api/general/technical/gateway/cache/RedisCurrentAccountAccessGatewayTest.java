// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.InvalidValueReason;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisCurrentAccountAccessGatewayTest {

  private static final UUID USER_ID = UUID.fromString("7dd355ee-1aa6-4cc0-939b-b9898ef9178d");
  private static final UUID OTHER_USER_ID = UUID.fromString("a8768297-2690-4ec6-a7d7-cc10fa114d77");
  private static final Duration TTL = Duration.ofMinutes(15);
  private static final CurrentAccountAccess ACCESS =
      new CurrentAccountAccess(USER_ID, true, false, 7, 11);

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private SimpleMeterRegistry meterRegistry;
  private CurrentAccountCacheTelemetry telemetry;
  private RedisCurrentAccountAccessGateway gateway;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    meterRegistry = new SimpleMeterRegistry();
    telemetry = new CurrentAccountCacheTelemetry(meterRegistry);
    gateway = new RedisCurrentAccountAccessGateway(redisTemplate, objectMapper, TTL, telemetry);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    meterRegistry.close();
  }

  @Test
  void populate_writesVersionedPerUserValueWithFixedTtl() {
    useValueOperations();
    var payload = ArgumentCaptor.forClass(String.class);

    gateway.populate(ACCESS);

    verify(valueOperations)
        .set(eq(RedisCurrentAccountAccessGateway.key(USER_ID)), payload.capture(), eq(TTL));
    Map<?, ?> cached = objectMapper.readValue(payload.getValue(), Map.class);
    assertEquals(1, cached.get("contractVersion"));
    assertEquals(USER_ID.toString(), cached.get("internalUserId"));
    assertEquals(true, cached.get("active"));
    assertEquals(false, cached.get("administrator"));
    assertEquals(7, cached.get("sessionGeneration"));
    assertEquals(11, cached.get("accountRevision"));
    assertEquals(
        "opencop:auth:current-account:v" + cached.get("contractVersion") + ":" + USER_ID,
        RedisCurrentAccountAccessGateway.key(USER_ID));
  }

  @Test
  void find_validValueReturnsEveryAccountFieldWithoutRefreshingTtl() {
    useValueOperations();
    when(valueOperations.get(RedisCurrentAccountAccessGateway.key(USER_ID)))
        .thenReturn(payload(1, USER_ID, true, false, 7, 11));

    var result = gateway.findByInternalUserId(USER_ID);

    assertEquals(ACCESS, result.orElseThrow());
    assertCacheCounter("hit", "none", 1.0d);
    verify(valueOperations).get(RedisCurrentAccountAccessGateway.key(USER_ID));
    verifyNoMoreInteractions(valueOperations);
    verify(redisTemplate, never()).expire(any(String.class), any(Duration.class));
  }

  @Test
  void find_missingValueRecordsOrdinaryMiss() {
    useValueOperations();
    when(valueOperations.get(RedisCurrentAccountAccessGateway.key(USER_ID))).thenReturn(null);

    assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());

    assertCacheCounter("miss", "none", 1.0d);
  }

  @Test
  void find_invalidValuesMapToAllowlistedReasonsAndRemainSafeMisses() {
    useValueOperations();
    List<InvalidPayload> rejectedValues =
        List.of(
            new InvalidPayload("not-json", InvalidValueReason.UNREADABLE_JSON),
            new InvalidPayload("[]", InvalidValueReason.NOT_OBJECT),
            new InvalidPayload(
                objectMapper.writeValueAsString(
                    Map.of("contractVersion", 1, "internalUserId", USER_ID)),
                InvalidValueReason.MISSING_FIELD),
            new InvalidPayload(
                payloadWithWrongScalarType("active", "true"), InvalidValueReason.WRONG_TYPE),
            new InvalidPayload(
                payload(2, USER_ID, true, false, 7, 11), InvalidValueReason.UNSUPPORTED_VERSION),
            new InvalidPayload(
                payload(1, OTHER_USER_ID, true, false, 7, 11), InvalidValueReason.USER_ID_MISMATCH),
            new InvalidPayload(
                payload(1, USER_ID, true, false, -1, 11), InvalidValueReason.NEGATIVE_VALUE));

    for (InvalidPayload rejectedValue : rejectedValues) {
      when(valueOperations.get(RedisCurrentAccountAccessGateway.key(USER_ID)))
          .thenReturn(rejectedValue.payload());
      assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());
      assertCacheCounter("invalid_value", rejectedValue.reason().value(), 1.0d);
    }
  }

  @Test
  void redisReadAndPopulationFailuresAreFailOpen() {
    useValueOperations();
    when(valueOperations.get(RedisCurrentAccountAccessGateway.key(USER_ID)))
        .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

    assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());
    assertCacheCounter("read_failure", "none", 1.0d);

    doThrow(new RedisConnectionFailureException("Redis unavailable"))
        .when(valueOperations)
        .set(eq(RedisCurrentAccountAccessGateway.key(USER_ID)), any(String.class), eq(TTL));
    assertDoesNotThrow(() -> gateway.populate(ACCESS));
    assertCacheCounter("populate_failure", "none", 1.0d);
  }

  @Test
  void telemetryFailuresDoNotChangeLookupOrFailOpenRedisBehavior() {
    useFailingTelemetry();
    useValueOperations();
    var readFailure = new RedisConnectionFailureException("Redis read unavailable");
    when(valueOperations.get(RedisCurrentAccountAccessGateway.key(USER_ID)))
        .thenReturn(payload(1, USER_ID, true, false, 7, 11))
        .thenReturn(null)
        .thenReturn("[]")
        .thenThrow(readFailure);

    assertEquals(ACCESS, gateway.findByInternalUserId(USER_ID).orElseThrow());
    assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());
    assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());
    assertTrue(gateway.findByInternalUserId(USER_ID).isEmpty());

    doThrow(new RedisConnectionFailureException("Redis population unavailable"))
        .when(valueOperations)
        .set(eq(RedisCurrentAccountAccessGateway.key(USER_ID)), any(String.class), eq(TTL));
    assertDoesNotThrow(() -> gateway.populate(ACCESS));
  }

  @Test
  void requiredEvictionFailureRecordsRollbackStageAndRethrows() {
    var exception = new RedisConnectionFailureException("Redis unavailable");
    doThrow(exception).when(redisTemplate).delete(RedisCurrentAccountAccessGateway.key(USER_ID));

    assertThrows(RedisConnectionFailureException.class, () -> gateway.evict(USER_ID));

    assertMutationCounter("required_precommit_evict", "rolled_back", 1.0d);
  }

  @Test
  void telemetryFailureDoesNotFailSuccessfulRequiredEviction() {
    useFailingTelemetry();

    assertDoesNotThrow(() -> gateway.evict(USER_ID));

    verify(redisTemplate).delete(RedisCurrentAccountAccessGateway.key(USER_ID));
  }

  @Test
  void telemetryFailureDoesNotReplaceRequiredRedisFailure() {
    useFailingTelemetry();
    var redisFailure = new RedisConnectionFailureException("Redis unavailable");
    doThrow(redisFailure).when(redisTemplate).delete(RedisCurrentAccountAccessGateway.key(USER_ID));

    var propagated =
        assertThrows(RedisConnectionFailureException.class, () -> gateway.evict(USER_ID));

    assertSame(redisFailure, propagated);
  }

  @Test
  void postCommitEvictionFailureRecordsCommittedRepairStageAndRemainsNonFatal() {
    var exception = new RedisConnectionFailureException("Redis unavailable");
    doThrow(exception).when(redisTemplate).delete(RedisCurrentAccountAccessGateway.key(USER_ID));
    TransactionSynchronizationManager.initSynchronization();
    gateway.scheduleEvictionAfterCommit(USER_ID);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
    TransactionSynchronizationManager.clearSynchronization();

    assertDoesNotThrow(synchronization::afterCommit);

    assertMutationCounter("best_effort_postcommit_evict", "committed_repair_failed", 1.0d);
  }

  @Test
  void telemetryFailureDoesNotChangeNonFatalPostCommitRepair() {
    useFailingTelemetry();
    doThrow(new RedisConnectionFailureException("Redis unavailable"))
        .when(redisTemplate)
        .delete(RedisCurrentAccountAccessGateway.key(USER_ID));
    TransactionSynchronizationManager.initSynchronization();
    gateway.scheduleEvictionAfterCommit(USER_ID);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
    TransactionSynchronizationManager.clearSynchronization();

    assertDoesNotThrow(synchronization::afterCommit);
  }

  @Test
  void successfulEvictionsRecordBothFixedStages() {
    gateway.evict(USER_ID);
    TransactionSynchronizationManager.initSynchronization();
    gateway.scheduleEvictionAfterCommit(USER_ID);
    var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
    TransactionSynchronizationManager.clearSynchronization();
    synchronization.afterCommit();

    assertMutationCounter("required_precommit_evict", "succeeded", 1.0d);
    assertMutationCounter("best_effort_postcommit_evict", "succeeded", 1.0d);
  }

  @Test
  void constructorRejectsNonPositiveTtl() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RedisCurrentAccountAccessGateway(
                redisTemplate, objectMapper, Duration.ZERO, telemetry));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RedisCurrentAccountAccessGateway(
                redisTemplate, objectMapper, Duration.ofSeconds(-1), telemetry));
  }

  @Test
  void keyIsScopedByInternalUserId() {
    assertFalse(
        RedisCurrentAccountAccessGateway.key(USER_ID)
            .equals(RedisCurrentAccountAccessGateway.key(OTHER_USER_ID)));
    assertTrue(RedisCurrentAccountAccessGateway.key(USER_ID).endsWith(USER_ID.toString()));
  }

  private void useValueOperations() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  private void useFailingTelemetry() {
    MeterRegistry failingRegistry = mock(MeterRegistry.class);
    when(failingRegistry.counter(anyString(), any(String[].class)))
        .thenThrow(new IllegalStateException("meter unavailable"));
    gateway =
        new RedisCurrentAccountAccessGateway(
            redisTemplate, objectMapper, TTL, new CurrentAccountCacheTelemetry(failingRegistry));
  }

  private void assertCacheCounter(String outcome, String reason, double expectedCount) {
    Counter counter =
        meterRegistry
            .find(CurrentAccountCacheTelemetry.CACHE_OUTCOMES_TOTAL)
            .tags("outcome", outcome, "reason", reason)
            .counter();
    assertNotNull(counter);
    assertEquals(expectedCount, counter.count());
  }

  private void assertMutationCounter(String stage, String outcome, double expectedCount) {
    Counter counter =
        meterRegistry
            .find(CurrentAccountCacheTelemetry.CACHE_MUTATIONS_TOTAL)
            .tags("stage", stage, "outcome", outcome)
            .counter();
    assertNotNull(counter);
    assertEquals(expectedCount, counter.count());
  }

  private String payload(
      int contractVersion,
      UUID internalUserId,
      boolean active,
      boolean administrator,
      long sessionGeneration,
      long accountRevision) {
    return objectMapper.writeValueAsString(
        Map.of(
            "contractVersion", contractVersion,
            "internalUserId", internalUserId,
            "active", active,
            "administrator", administrator,
            "sessionGeneration", sessionGeneration,
            "accountRevision", accountRevision));
  }

  private String payloadWithWrongScalarType(String field, Object replacement) {
    var value =
        new HashMap<String, Object>(
            Map.of(
                "contractVersion",
                1,
                "internalUserId",
                USER_ID,
                "active",
                true,
                "administrator",
                false,
                "sessionGeneration",
                7,
                "accountRevision",
                11));
    value.put(field, replacement);
    return objectMapper.writeValueAsString(value);
  }

  private record InvalidPayload(String payload, InvalidValueReason reason) {}
}
