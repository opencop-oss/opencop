// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalTracksAdmissionGatewayTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");

  private RedisHistoricalTracksAdmissionGateway gateway;
  private RecordingStringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    redisTemplate = new RecordingStringRedisTemplate();
    gateway =
        new RedisHistoricalTracksAdmissionGateway(redisTemplate, new CoreHistoricalRedisScripts());
  }

  @Test
  void requestCancellationForActiveJobs_marksOnlyMatchingUserJobs() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    var indexedUserJobId = UUID.randomUUID();
    var unindexedUserJobId = UUID.randomUUID();
    var otherUserJobId = UUID.randomUUID();
    var indexedUserKey = "opencop:history:job:" + indexedUserJobId;
    var unindexedUserKey = "opencop:history:job:" + unindexedUserJobId;
    var otherUserKey = "opencop:history:job:" + otherUserJobId;
    redisTemplate.putHash(
        indexedUserKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putHash(
        unindexedUserKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putHash(
        otherUserKey,
        Map.of(
            "requestedBy",
            OTHER_USER_ID.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putUserIndex(
        USER_ID, Set.of(indexedUserJobId.toString(), otherUserJobId.toString()));

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(1, changedJobs);
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(),
        redisTemplate.getHash(indexedUserKey, "stage"));
    assertEquals(
        HistoricalJobStage.EXECUTION_RUNNING.name(),
        redisTemplate.getHash(unindexedUserKey, "stage"));
    assertEquals(
        HistoricalJobStage.EXECUTION_RUNNING.name(), redisTemplate.getHash(otherUserKey, "stage"));
    assertEquals(0, redisTemplate.scanCallCount);
  }

  @Test
  void requestCancellationForActiveJobs_usesTheSelectedClusterControlKeyLayout() {
    var layout = HistoricalJobControlKeyLayout.CLUSTER;
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    var jobId = UUID.randomUUID();
    var jobKey = layout.jobState(jobId);
    redisTemplate.putHash(
        jobKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.sortedSets.put(
        layout.userJobIndex(USER_ID), new ArrayList<>(List.of(jobId.toString())));
    var clusterGateway =
        new RedisHistoricalTracksAdmissionGateway(
            redisTemplate, layout, new CoreHistoricalRedisScripts());

    assertEquals(1, clusterGateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId));
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(), redisTemplate.getHash(jobKey, "stage"));
  }

  @Test
  void requestCancellationForActiveJobs_returnsZeroWhenNoJobsChange() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    var terminalKey = "opencop:history:job:" + UUID.randomUUID();
    redisTemplate.putHash(
        terminalKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "status",
            "COMPLETED",
            "stage",
            HistoricalJobStage.EXECUTION_COMPLETED.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(0, changedJobs);
    assertEquals(
        HistoricalJobStage.EXECUTION_COMPLETED.name(), redisTemplate.getHash(terminalKey, "stage"));
  }

  @Test
  void requestCancellationForActiveJobs_returnsZeroWhenStatusFieldIsMissing() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    var missingStatusKey = "opencop:history:job:" + UUID.randomUUID();
    redisTemplate.putHash(
        missingStatusKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "before",
            "updatedAt",
            "2026-05-21T11:00:00Z"));

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(0, changedJobs);
    assertEquals(
        HistoricalJobStage.EXECUTION_RUNNING.name(),
        redisTemplate.getHash(missingStatusKey, "stage"));
  }

  @Test
  void requestCancellationForActiveJobs_skipsReplacementRequestAndCancelsPredecessor() {
    var predecessorRequestId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    var replacementRequestId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    var predecessorJobId = UUID.randomUUID();
    var replacementJobId = UUID.randomUUID();
    var predecessorKey = "opencop:history:job:" + predecessorJobId;
    var replacementKey = "opencop:history:job:" + replacementJobId;
    redisTemplate.putHash(
        predecessorKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "requestId",
            predecessorRequestId.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putHash(
        replacementKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "requestId",
            replacementRequestId.toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putUserIndex(
        USER_ID, Set.of(predecessorJobId.toString(), replacementJobId.toString()));

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(1, changedJobs);
    assertTrue(
        redisTemplate.lastScriptSource.contains("-- Script: cancel_job_if_active_and_owned"));
    assertTrue(redisTemplate.lastScriptSource.contains("-- ARGV[14]: updatedAt value"));
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(),
        redisTemplate.getHash(predecessorKey, "stage"));
    assertEquals(
        HistoricalJobStage.EXECUTION_RUNNING.name(),
        redisTemplate.getHash(replacementKey, "stage"));
  }

  @Test
  void requestCancellationForActiveJobs_removesMalformedIndexEntryAndContinues() {
    var replacementRequestId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    var validJobId = UUID.randomUUID();
    var validKey = "opencop:history:job:" + validJobId;
    redisTemplate.putHash(
        validKey,
        Map.of(
            "requestedBy",
            USER_ID.toString(),
            "requestId",
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").toString(),
            "status",
            "RUNNING",
            "stage",
            HistoricalJobStage.EXECUTION_RUNNING.name(),
            "failureReason",
            "",
            "updatedAt",
            "2026-05-21T11:00:00Z"));
    redisTemplate.putUserIndex(USER_ID, Set.of("not-a-uuid", validJobId.toString()));

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(1, changedJobs);
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(), redisTemplate.getHash(validKey, "stage"));
    assertEquals(Set.of(validJobId.toString()), redisTemplate.userIndexMembers(USER_ID));
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {
    private final Map<String, Map<Object, Object>> hashes = new HashMap<>();
    private final Map<String, List<String>> sortedSets = new HashMap<>();
    private String lastScriptSource = "";
    private int scanCallCount = 0;

    @Override
    @SuppressWarnings("unchecked")
    public HashOperations<String, Object, Object> opsForHash() {
      return (HashOperations<String, Object, Object>)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {HashOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "get" -> hashes.getOrDefault((String) args[0], Map.of()).get(args[1]);
                  case "put" -> {
                    hashes
                        .computeIfAbsent((String) args[0], ignored -> new HashMap<>())
                        .put(args[1], args[2]);
                    yield null;
                  }
                  case "putAll" -> {
                    hashes.put((String) args[0], new HashMap<>((Map<?, ?>) args[1]));
                    yield null;
                  }
                  case "entries" -> new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }

    @Override
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
      lastScriptSource = script.getScriptAsString();
      String key = keys.get(0);
      Map<Object, Object> hash = hashes.getOrDefault(key, Map.of());
      Object requestedBy = hash.get(args[0]);
      if (requestedBy == null || !args[1].toString().equals(requestedBy.toString())) {
        return script.getResultType().cast(Long.valueOf(0L));
      }
      Object status = hash.get(args[2]);
      if (status == null
          || args[3].toString().equals(status.toString())
          || args[4].toString().equals(status.toString())
          || args[5].toString().equals(status.toString())) {
        return script.getResultType().cast(Long.valueOf(0L));
      }
      Object requestId = hash.get(args[6]);
      if (requestId != null && args[7].toString().equals(requestId.toString())) {
        return script.getResultType().cast(Long.valueOf(0L));
      }
      hashes.computeIfAbsent(key, ignored -> new HashMap<>()).put(args[8], args[9]);
      hashes.computeIfAbsent(key, ignored -> new HashMap<>()).put(args[10], args[11]);
      hashes.computeIfAbsent(key, ignored -> new HashMap<>()).put(args[12], args[13]);
      return script.getResultType().cast(Long.valueOf(1L));
    }

    @Override
    public Boolean hasKey(String key) {
      return hashes.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ZSetOperations<String, String> opsForZSet() {
      return (ZSetOperations<String, String>)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {ZSetOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "range" -> {
                    String key = (String) args[0];
                    yield Set.copyOf(sortedSets.getOrDefault(key, List.of()));
                  }
                  case "remove" -> {
                    String key = (String) args[0];
                    List<String> members = sortedSets.getOrDefault(key, new ArrayList<>());
                    long removed = 0L;
                    Object[] values =
                        (args.length == 2 && args[1] instanceof Object[] varargArray)
                            ? varargArray
                            : java.util.Arrays.copyOfRange(args, 1, args.length);
                    for (Object value : values) {
                      if (members.remove(value.toString())) {
                        removed++;
                      }
                    }
                    sortedSets.put(key, members);
                    yield removed;
                  }
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
      scanCallCount++;
      List<String> keys = new ArrayList<>();
      for (var key : hashes.keySet()) {
        if (key.startsWith("opencop:history:job:")) {
          keys.add(key);
        }
      }
      return new Cursor<>() {
        private int index = 0;
        private boolean closed = false;

        @Override
        public String next() {
          return keys.get(index++);
        }

        @Override
        public boolean hasNext() {
          return index < keys.size();
        }

        @Override
        public void close() {
          closed = true;
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
      };
    }

    void putHash(String key, Map<String, String> values) {
      hashes.put(key, new HashMap<>(values));
    }

    void putUserIndex(UUID userId, Set<String> jobIds) {
      sortedSets.put("opencop:history:job-index:user:" + userId, new ArrayList<>(jobIds));
    }

    String getHash(String key, String field) {
      Object value = hashes.getOrDefault(key, Map.of()).get(field);
      return value == null ? null : value.toString();
    }

    Set<String> userIndexMembers(UUID userId) {
      return Set.copyOf(
          sortedSets.getOrDefault("opencop:history:job-index:user:" + userId, List.of()));
    }
  }
}
