// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.history;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HistoricalRedactionStaticTest {

  private static final List<ForbiddenToken> FORBIDDEN_TOKENS =
      List.of(
          new ForbiddenToken("object_id"),
          new ForbiddenToken("objectid"),
          new ForbiddenToken("requestedby"),
          new ForbiddenToken("userid"),
          new ForbiddenToken("latitude"),
          new ForbiddenToken("longitude"),
          new ForbiddenToken("payloadKey"),
          new ForbiddenToken("chunkStateKey"),
          new ForbiddenToken("opencop:history:payload:"),
          new ForbiddenToken("opencop:history:chunk-state:"));

  private static final Set<String> EXPECTED_ALLOWED_KEYS =
      Set.of(
          "requestId",
          "jobId",
          "chunkIndex",
          "subjectType",
          "samplerVersion",
          "profile",
          "history.active_snapshot.read_path_candidate",
          "history.active_snapshot.source_count",
          "history.active_snapshot.chunk_from",
          "history.active_snapshot.chunk_to",
          "history.active_snapshot.fetch_size",
          "history.active_snapshot.timeout_seconds");

  private static final Pattern LOG_CALL_PATTERN =
      Pattern.compile("log\\.(warn|error)\\s*\\((.*?)\\);", Pattern.DOTALL);
  private static final Pattern ATTR_KEY_PATTERN =
      Pattern.compile("attributes\\.put\\(\"([^\"]+)\"");

  private static final List<Path> HISTORICAL_SOURCES =
      List.of(
          Path.of(
              "processor/src/main/java/com/opencop/processor/business/usecase/history/HistoricalChunkExecutionCoordinator.java"),
          Path.of(
              "processor/src/main/java/com/opencop/processor/business/usecase/history/HistoricalSubjectLaneScheduler.java"),
          Path.of(
              "processor/src/main/java/com/opencop/processor/technical/gateway/history/HistoricalJobIngressService.java"),
          Path.of(
              "processor/src/main/java/com/opencop/processor/technical/gateway/history/HistoryJobPlanPublisher.java"),
          Path.of(
              "processor/src/main/java/com/opencop/processor/technical/gateway/history/RedisHistoricalChunkReadyNotifier.java"),
          Path.of(
              "processor/src/main/java/com/opencop/processor/technical/spring/consumer/HistoricalTracksConsumer.java"),
          Path.of(
              "core/src/main/java/com/opencop/core/api/event/technical/observability/history/HistoricalSubmissionTracing.java"),
          Path.of(
              "websocket/src/main/java/com/opencop/websocket/business/usecase/history/UserHistoryChunkSender.java"),
          Path.of(
              "websocket/src/main/java/com/opencop/websocket/business/usecase/history/UserHistoryEventHandler.java"),
          Path.of(
              "sdk/src/main/java/com/opencop/core/sdk/observability/history/HistoricalTraceContext.java"));

  @Test
  void sourceLogsAndHistoricalAttributesDoNotContainForbiddenTokens() throws IOException {
    List<String> violations = new ArrayList<>();
    Set<String> discoveredAttributeKeys = new LinkedHashSet<>();
    String traceContextSource = null;
    Path repositoryRoot = repositoryRoot();

    for (Path relativePath : HISTORICAL_SOURCES) {
      Path path = repositoryRoot.resolve(relativePath);
      String source = Files.readString(path);
      violations.addAll(scanLogCalls(path, source));
      discoveredAttributeKeys.addAll(scanAttributeKeys(source));
      if (relativePath.endsWith("HistoricalTraceContext.java")) {
        traceContextSource = source;
      }
    }

    assertTrue(traceContextSource != null, "HistoricalTraceContext source should be scanned");
    for (String key : EXPECTED_ALLOWED_KEYS) {
      assertTrue(
          traceContextSource.contains("\"" + key + "\""),
          "Expected allowlisted key was not found in HistoricalTraceContext: " + key);
    }
    List<String> attributeViolations =
        scanAttributeKeyViolations(Path.of("historical-sources"), discoveredAttributeKeys);
    assertTrue(attributeViolations.isEmpty(), String.join("\n", attributeViolations));
    assertTrue(violations.isEmpty(), String.join("\n", violations));
  }

  @Test
  void repositoryHistoricalSourcesResolveFromTheRepositoryRoot() {
    Path repositoryRoot = repositoryRoot();

    assertTrue(
        Files.exists(
            repositoryRoot.resolve(
                "processor/src/main/java/com/opencop/processor/business/usecase/history/HistoricalChunkExecutionCoordinator.java")),
        "processor historical sources should resolve from the repository root");
    assertTrue(
        Files.exists(
            repositoryRoot.resolve(
                "processor/src/main/java/com/opencop/processor/technical/gateway/history/RedisHistoricalChunkReadyNotifier.java")),
        "processor notifier source should resolve from the repository root");
  }

  @Test
  void fixtureFailsClosedAndNamesTheForbiddenToken() {
    String fixture =
        """
        log.warn("fixture includes requestedBy and latitude");
        log.error("fixture includes objectId");
        """;

    List<String> violations = scanLogCalls(Path.of("fixture"), fixture);
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.contains("requestedby")),
        "fixture scan should name requestedBy");
  }

  @Test
  void cacheKeyVariableFixtureReportsBothForbiddenVariableNames() {
    String fixture =
        """
        log.warn("x {}", payloadKey);
        log.error("x {}", chunkStateKey);
        """;

    List<String> violations = scanLogCalls(Path.of("cache-key-fixture"), fixture);

    assertTrue(
        violations.stream().anyMatch(v -> v.contains("payloadKey")),
        "fixture scan should name payloadKey");
    assertTrue(
        violations.stream().anyMatch(v -> v.contains("chunkStateKey")),
        "fixture scan should name chunkStateKey");
  }

  @Test
  void cacheKeyPrefixFixtureReportsBothForbiddenPrefixes() {
    String fixture =
        """
        log.warn("x opencop:history:payload:SOURCE");
        log.error("x opencop:history:chunk-state:SOURCE");
        """;

    List<String> violations = scanLogCalls(Path.of("cache-key-prefix-fixture"), fixture);

    assertTrue(
        violations.stream().anyMatch(v -> v.contains("opencop:history:payload:")),
        "fixture scan should name the payload key prefix");
    assertTrue(
        violations.stream().anyMatch(v -> v.contains("opencop:history:chunk-state:")),
        "fixture scan should name the chunk-state key prefix");
  }

  @Test
  void spanAttributeFixtureReportsForbiddenCacheKeyAttribute() {
    String fixture = "attributes.put(\"payloadKey\", \"x\");";
    Set<String> discovered = scanAttributeKeys(fixture);
    List<String> violations = scanAttributeKeyViolations(Path.of("attribute-fixture"), discovered);

    assertTrue(
        violations.stream().anyMatch(v -> v.contains("payloadKey")),
        "fixture scan should name payloadKey");
  }

  @Test
  void attributeFixtureFailsClosedAndNamesForbiddenAttributeKey() {
    String fixture = "attributes.put(\"userId\", \"x\");";
    Set<String> discovered = scanAttributeKeys(fixture);
    List<String> violations = scanAttributeKeyViolations(Path.of("attribute-fixture"), discovered);
    assertFalse(violations.isEmpty(), "fixture should fail closed on forbidden attribute key");
    assertTrue(
        violations.stream().anyMatch(v -> v.contains("userid") || v.contains("userId")),
        "fixture scan should name userId");
  }

  private static List<String> scanLogCalls(Path path, String source) {
    List<String> violations = new ArrayList<>();
    Matcher matcher = LOG_CALL_PATTERN.matcher(source);
    while (matcher.find()) {
      String call = matcher.group(2);
      String lowerCall = call.toLowerCase(Locale.ROOT);
      for (ForbiddenToken forbidden : FORBIDDEN_TOKENS) {
        if (lowerCall.contains(forbidden.normalized())) {
          violations.add(
              path
                  + " contains forbidden token '"
                  + forbidden.display()
                  + "' in "
                  + matcher.group(1));
        }
      }
    }
    return violations;
  }

  private static Set<String> scanAttributeKeys(String source) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = ATTR_KEY_PATTERN.matcher(source);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  private static List<String> scanAttributeKeyViolations(Path path, Set<String> discoveredKeys) {
    List<String> violations = new ArrayList<>();
    for (String key : discoveredKeys) {
      if (!EXPECTED_ALLOWED_KEYS.contains(key)) {
        violations.add(path + " discovered span attribute key is outside the allowlist: " + key);
      }
      String lowerKey = key.toLowerCase(Locale.ROOT);
      for (ForbiddenToken forbidden : FORBIDDEN_TOKENS) {
        if (lowerKey.contains(forbidden.normalized())) {
          violations.add(
              path
                  + " discovered span attribute key contains forbidden token '"
                  + forbidden.display()
                  + "': "
                  + key);
        }
      }
    }
    return violations;
  }

  private static Path repositoryRoot() {
    return Stream.iterate(
            Path.of("").toAbsolutePath(), path -> path.getParent() != null, Path::getParent)
        .filter(path -> Files.exists(path.resolve("processor/pom.xml")))
        .filter(path -> Files.exists(path.resolve("core/pom.xml")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Repository root not found"));
  }

  private record ForbiddenToken(String display) {
    private String normalized() {
      return display.toLowerCase(Locale.ROOT);
    }
  }
}
