// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalAdmissionActiveJobsTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
  private static final UUID HEALTHY_JOB_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440012");

  @Test
  void scanEntries_clusterDataAccessStateReadRetainsMemberAndSkipsObservation() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    var failure = new RedisConnectionFailureException("Redis state read unavailable");
    when(stateGateway.findByJobId(JOB_ID)).thenThrow(failure);

    var entries =
        new RedisHistoricalAdmissionActiveJobs(redis, layout)
            .scanEntries(stateGateway, ignored -> false);

    assertTrue(entries.isEmpty());
    assertTrue(redis.members(activeRunningKey).contains(JOB_ID.toString()));
    verify(stateGateway).findByJobId(JOB_ID);
  }

  @Test
  void scanEntries_standaloneDataAccessStateReadRemovesMemberAndSkipsObservation() {
    var layout = HistoricalJobControlKeyLayout.STANDALONE;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID))
        .thenThrow(new RedisConnectionFailureException("Redis state read unavailable"));

    var entries =
        new RedisHistoricalAdmissionActiveJobs(redis, layout)
            .scanEntries(stateGateway, ignored -> false);

    assertTrue(entries.isEmpty());
    assertFalse(redis.members(activeRunningKey).contains(JOB_ID.toString()));
  }

  @Test
  void scanEntries_clusterNonDataAccessStateFailureRemovesMalformedMember() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID)).thenThrow(new IllegalArgumentException("bad state"));

    var entries =
        new RedisHistoricalAdmissionActiveJobs(redis, layout)
            .scanEntries(stateGateway, ignored -> false);

    assertTrue(entries.isEmpty());
    assertFalse(redis.members(activeRunningKey).contains(JOB_ID.toString()));
  }

  @Test
  void scanEntries_successfulMissingStateProducesOrdinaryEntry() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID)).thenReturn(Optional.empty());

    var entries =
        new RedisHistoricalAdmissionActiveJobs(redis, layout)
            .scanEntries(stateGateway, ignored -> false);

    assertEquals(1, entries.size());
    assertEquals(JOB_ID, entries.getFirst().jobId());
    assertTrue(entries.getFirst().state().isEmpty());
  }

  @Test
  void scanEntries_failedReadThenHealthyReadRetainsAndLaterReturnsMember() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID))
        .thenThrow(new RedisConnectionFailureException("Redis state read unavailable"))
        .thenReturn(Optional.empty());
    var activeJobs = new RedisHistoricalAdmissionActiveJobs(redis, layout);

    assertTrue(activeJobs.scanEntries(stateGateway, ignored -> false).isEmpty());
    assertTrue(redis.members(activeRunningKey).contains(JOB_ID.toString()));

    var healthyEntries = activeJobs.scanEntries(stateGateway, ignored -> false);

    assertEquals(1, healthyEntries.size());
    assertEquals(JOB_ID, healthyEntries.getFirst().jobId());
    assertTrue(healthyEntries.getFirst().state().isEmpty());
  }

  @Test
  void scanEntries_failedMemberReadThenHealthyMemberContinuesSamePass() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = layout.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    redis.add(activeRunningKey, HEALTHY_JOB_ID.toString());
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID))
        .thenThrow(new RedisConnectionFailureException("Redis state read unavailable"));
    when(stateGateway.findByJobId(HEALTHY_JOB_ID)).thenReturn(Optional.empty());

    var entries =
        new RedisHistoricalAdmissionActiveJobs(redis, layout)
            .scanEntries(stateGateway, ignored -> false);

    assertEquals(1, entries.size());
    assertEquals(HEALTHY_JOB_ID, entries.getFirst().jobId());
    assertTrue(redis.members(activeRunningKey).contains(JOB_ID.toString()));
    assertTrue(redis.members(activeRunningKey).contains(HEALTHY_JOB_ID.toString()));
  }

  @Test
  void scanEntries_firstScanFailurePropagates() {
    var redis = new RecordingRedisTemplate();
    redis.scanFailure = new RedisConnectionFailureException("Redis scan unavailable");

    assertThrows(
        RedisConnectionFailureException.class,
        () ->
            new RedisHistoricalAdmissionActiveJobs(redis, HistoricalJobControlKeyLayout.CLUSTER)
                .scanEntries(mock(HistoricalJobStateGateway.class), ignored -> false));
  }

  @Test
  void scanEntries_memberReadFailurePropagates() {
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = HistoricalJobControlKeyLayout.CLUSTER.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    redis.membersFailure = new RedisConnectionFailureException("Redis members unavailable");

    assertThrows(
        RedisConnectionFailureException.class,
        () ->
            new RedisHistoricalAdmissionActiveJobs(redis, HistoricalJobControlKeyLayout.CLUSTER)
                .scanEntries(mock(HistoricalJobStateGateway.class), ignored -> false));
  }

  @Test
  void scanEntries_malformedMemberCleanupFailurePropagates() {
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = HistoricalJobControlKeyLayout.CLUSTER.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, "not-a-uuid");
    redis.removeFailure = new RedisConnectionFailureException("Redis cleanup unavailable");

    assertThrows(
        RedisConnectionFailureException.class,
        () ->
            new RedisHistoricalAdmissionActiveJobs(redis, HistoricalJobControlKeyLayout.CLUSTER)
                .scanEntries(mock(HistoricalJobStateGateway.class), ignored -> false));
  }

  @Test
  void scanEntries_malformedMemberCleanupHappensBeforeLaterDiscoveryFailure() {
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = HistoricalJobControlKeyLayout.CLUSTER.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, "not-a-uuid");
    redis.scanKeys = List.of(activeRunningKey, "later-discovery-key");
    redis.lateScanFailure = new RedisConnectionFailureException("Redis scan failed later");

    assertThrows(
        RedisConnectionFailureException.class,
        () ->
            new RedisHistoricalAdmissionActiveJobs(redis, HistoricalJobControlKeyLayout.CLUSTER)
                .scanEntries(mock(HistoricalJobStateGateway.class), ignored -> false));

    assertFalse(redis.members(activeRunningKey).contains("not-a-uuid"));
    assertTrue(redis.lastCursorClosed);
  }

  @Test
  void scanEntries_malformedStoredStateCleanupHappensBeforeLaterDiscoveryFailure() {
    var redis = new RecordingRedisTemplate();
    var activeRunningKey = HistoricalJobControlKeyLayout.CLUSTER.activeRunningKey(USER_ID);
    redis.add(activeRunningKey, JOB_ID.toString());
    redis.scanKeys = List.of(activeRunningKey, "later-discovery-key");
    redis.lateScanFailure = new RedisConnectionFailureException("Redis scan failed later");
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(JOB_ID)).thenThrow(new IllegalArgumentException("bad state"));

    assertThrows(
        RedisConnectionFailureException.class,
        () ->
            new RedisHistoricalAdmissionActiveJobs(redis, HistoricalJobControlKeyLayout.CLUSTER)
                .scanEntries(stateGateway, ignored -> false));

    assertFalse(redis.members(activeRunningKey).contains(JOB_ID.toString()));
    assertTrue(redis.lastCursorClosed);
  }

  private static final class RecordingRedisTemplate extends StringRedisTemplate {

    private final Map<String, Set<String>> activeMembers = new LinkedHashMap<>();
    private final SetOperations<String, String> setOperations = setOperations();
    private List<String> scanKeys = new ArrayList<>();
    private RuntimeException scanFailure;
    private RuntimeException lateScanFailure;
    private RuntimeException membersFailure;
    private RuntimeException removeFailure;
    private boolean lastCursorClosed;

    @Override
    public SetOperations<String, String> opsForSet() {
      return setOperations;
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
      if (scanFailure != null) {
        throw scanFailure;
      }
      return new RecordingCursor(scanKeys, lateScanFailure, this);
    }

    void add(String key, String member) {
      activeMembers.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(member);
      if (scanKeys.isEmpty()) {
        scanKeys = List.of(key);
      }
    }

    Set<String> members(String key) {
      return Set.copyOf(activeMembers.getOrDefault(key, Set.of()));
    }

    @SuppressWarnings("unchecked")
    private SetOperations<String, String> setOperations() {
      return (SetOperations<String, String>)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {SetOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "members" -> {
                    if (membersFailure != null) {
                      throw membersFailure;
                    }
                    yield members((String) args[0]);
                  }
                  case "remove" -> {
                    if (removeFailure != null) {
                      throw removeFailure;
                    }
                    var key = (String) args[0];
                    var current = activeMembers.getOrDefault(key, new LinkedHashSet<>());
                    var removed = 0L;
                    for (int index = 1; index < args.length; index++) {
                      if (args[index] instanceof Object[] values) {
                        for (var value : values) {
                          if (current.remove(value.toString())) {
                            removed++;
                          }
                        }
                      } else if (current.remove(args[index].toString())) {
                        removed++;
                      }
                    }
                    yield removed;
                  }
                  case "toString" -> "RecordingSetOperations";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }
  }

  private static final class RecordingCursor implements Cursor<String> {

    private final List<String> keys;
    private final RuntimeException lateFailure;
    private final RecordingRedisTemplate owner;
    private int index;
    private boolean closed;

    private RecordingCursor(
        List<String> keys, RuntimeException lateFailure, RecordingRedisTemplate owner) {
      this.keys = List.copyOf(keys);
      this.lateFailure = lateFailure;
      this.owner = owner;
    }

    @Override
    public boolean hasNext() {
      if (lateFailure != null && index >= 1) {
        throw lateFailure;
      }
      return index < keys.size();
    }

    @Override
    public String next() {
      return keys.get(index++);
    }

    @Override
    public void close() {
      closed = true;
      owner.lastCursorClosed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    @Override
    public long getPosition() {
      return index;
    }

    @Override
    public long getCursorId() {
      return 0L;
    }

    @Override
    public Cursor.CursorId getId() {
      return Cursor.CursorId.initial();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  }
}
