// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * New Java multi-key commands or Lua require authentic cluster-safe evidence. This source-tree
 * census pins each Lua resource's supported topology and key boundary; it neither proves safe
 * semantics nor inspects inline Lua strings.
 */
class RedisLuaClusterDispositionCensusTest {

  private static final Pattern PRODUCTION_LUA_PATH =
      Pattern.compile("(^|.*/)src/main/resources/redis/.+\\.lua$");
  private static final Set<String> SAFE_RESOURCES =
      Set.of(
          "core/src/main/resources/redis/history/cancel_job_if_active_and_owned.lua",
          "processor/src/main/resources/redis/history/admission_read_queued_payload_cluster.lua",
          "processor/src/main/resources/redis/history/job_mark_cancellation_requested.lua",
          "processor/src/main/resources/redis/history/job_refresh_active_ttl.lua",
          "processor/src/main/resources/redis/history/job_update_with_guards.lua",
          "sdk/src/main/resources/redis/history/chunk_mark_delivery_terminal.lua",
          "sdk/src/main/resources/redis/history/job_update_with_guards.lua");
  private static final Map<String, Set<Integer>> PROVEN_SAME_SLOT_RESOURCES =
      Map.of(
          "processor/src/main/resources/redis/history/admission_cleanup_state_cluster.lua",
          Set.of(1, 2, 3, 4, 5, 6, 7),
          "processor/src/main/resources/redis/history/admission_register_cluster.lua",
          Set.of(1, 2, 3, 4, 5, 6, 7),
          "processor/src/main/resources/redis/history/admission_record_execution_exit.lua",
          Set.of(1, 2, 3, 4, 5, 6),
          "processor/src/main/resources/redis/history/admission_record_terminal.lua",
          Set.of(1, 2, 3, 4, 5, 6),
          "processor/src/main/resources/redis/history/job_create_received_if_absent.lua",
          Set.of(1, 2, 3, 4, 5, 6),
          "processor/src/main/resources/redis/geo_evict.lua",
          Set.of(1, 2, 3),
          "sdk/src/main/resources/redis/geo_unregister_if_empty.lua",
          Set.of(1, 2, 3),
          "sdk/src/main/resources/redis/geo_upsert.lua",
          Set.of(1, 2, 3, 4));
  private static final Set<String> STANDALONE_ONLY_RESOURCES =
      Set.of(
          "processor/src/main/resources/redis/history/admission_cleanup_state.lua",
          "processor/src/main/resources/redis/history/admission_register.lua");
  private static final Pattern NUMERIC_KEYS_INDEX =
      Pattern.compile("\\bKEYS\\s*\\[\\s*(\\d+)\\s*]");

  @Test
  void productionLuaResources_matchExactDisjointClusterDisposition() throws IOException {
    var repositoryRoot = findRepositoryRoot();
    var discoveredResources = discoverProductionLua(repositoryRoot);
    var dispositionedResources = new HashSet<String>();
    dispositionedResources.addAll(SAFE_RESOURCES);
    dispositionedResources.addAll(PROVEN_SAME_SLOT_RESOURCES.keySet());
    dispositionedResources.addAll(STANDALONE_ONLY_RESOURCES);

    assertAll(
        () -> assertEquals(7, SAFE_RESOURCES.size()),
        () -> assertEquals(8, PROVEN_SAME_SLOT_RESOURCES.size()),
        () -> assertEquals(2, STANDALONE_ONLY_RESOURCES.size()),
        () -> assertTrue(disjoint(SAFE_RESOURCES, PROVEN_SAME_SLOT_RESOURCES.keySet())),
        () -> assertTrue(disjoint(SAFE_RESOURCES, STANDALONE_ONLY_RESOURCES)),
        () -> assertTrue(disjoint(PROVEN_SAME_SLOT_RESOURCES.keySet(), STANDALONE_ONLY_RESOURCES)),
        () -> assertEquals(discoveredResources, dispositionedResources));

    for (var resource : SAFE_RESOURCES) {
      assertSafeResourceUsesAtMostOneKey(repositoryRoot.resolve(resource), resource);
    }

    for (var disposition : PROVEN_SAME_SLOT_RESOURCES.entrySet()) {
      assertProvenSameSlotResourceReferencesExactly(
          repositoryRoot.resolve(disposition.getKey()),
          disposition.getKey(),
          disposition.getValue());
    }
  }

  private static Path findRepositoryRoot() {
    for (var candidate = Path.of("").toAbsolutePath().normalize();
        candidate != null;
        candidate = candidate.getParent()) {
      if (isRepositoryRoot(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Could not identify the OpenCOP Java repository root");
  }

  private static boolean isRepositoryRoot(Path candidate) {
    return Files.isRegularFile(candidate.resolve("opencop-parent/pom.xml"))
        && Files.isRegularFile(candidate.resolve("sdk/pom.xml"));
  }

  private static Set<String> discoverProductionLua(Path repositoryRoot) throws IOException {
    try (var paths = Files.walk(repositoryRoot)) {
      return paths
          .filter(Files::isRegularFile)
          .map(repositoryRoot::relativize)
          .map(Path::toString)
          .map(path -> path.replace(File.separatorChar, '/'))
          .filter(path -> PRODUCTION_LUA_PATH.matcher(path).matches())
          .collect(Collectors.toSet());
    }
  }

  private static boolean disjoint(Set<String> left, Set<String> right) {
    return left.stream().noneMatch(right::contains);
  }

  /**
   * A proven-same-slot resource is deliberately multi-key. Pretending it were one-key safe would
   * weaken the permanent boundary, so this census pins its exact referenced key indexes instead and
   * leaves the slot evidence to the matching authentic three-primary component test.
   */
  private static void assertProvenSameSlotResourceReferencesExactly(
      Path path, String resource, Set<Integer> expectedKeyIndexes) throws IOException {
    var matcher = NUMERIC_KEYS_INDEX.matcher(Files.readString(path, StandardCharsets.UTF_8));
    var referenced = new TreeSet<Integer>();
    while (matcher.find()) {
      referenced.add(Integer.parseInt(matcher.group(1)));
    }
    assertEquals(new TreeSet<>(expectedKeyIndexes), referenced, resource + " key indexes");
  }

  private static void assertSafeResourceUsesAtMostOneKey(Path path, String resource)
      throws IOException {
    var matcher = NUMERIC_KEYS_INDEX.matcher(Files.readString(path, StandardCharsets.UTF_8));
    while (matcher.find()) {
      var index = Integer.parseInt(matcher.group(1));
      assertTrue(index <= 1, () -> resource + " references " + matcher.group());
    }
  }
}
