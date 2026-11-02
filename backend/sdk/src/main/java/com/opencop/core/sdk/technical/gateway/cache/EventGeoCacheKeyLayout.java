// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache;

import java.util.Objects;
import java.util.UUID;

/**
 * Topology-selected live-geo key family for one geo source.
 *
 * <p>{@link #STANDALONE} reproduces the exact legacy contract that {@link EventGeoCacheKeys} has
 * always produced, including its verbatim formatting of unusual identifiers, and records source
 * membership in the single global {@code opencop:event:sources} sorted set.
 *
 * <p>{@link #CLUSTER} wraps the source id in one Redis hash tag so every source-local key resolves
 * to one cluster slot, and replaces the global sorted set with one persistent per-source marker for
 * recovery. Normal discovery uses a separate retained global source set.
 *
 * <p>Selection is exhaustive. There is no boolean, nullable, or partially applied layout, and
 * {@link SourceKeys} validates itself so a half-standalone/half-cluster aggregate cannot exist.
 */
public enum EventGeoCacheKeyLayout {

  /** Exact legacy layout: untagged source keys plus one global source sorted set. */
  STANDALONE(RegistryKind.GLOBAL_SORTED_SET),

  /** Hash-tagged layout: one slot per source plus one persistent per-source marker. */
  CLUSTER(RegistryKind.PER_SOURCE_MARKER);

  static final String GEO_PREFIX = "opencop:event:geo";
  static final String GLOBAL_SOURCE_INDEX = "opencop:event:sources";

  private static final String EVENT_SUFFIX = ":event";
  private static final String UPDATED_AT_SUFFIX = ":updatedAt";
  private static final String STALE_AT_SUFFIX = ":staleAt";
  private static final String HYDRATION_LOCK_SUFFIX = ":hydrate:lock";
  private static final String REGISTRY_SUFFIX = ":registry";
  private static final String CLUSTER_MARKER_SCAN_PATTERN = GEO_PREFIX + ":{*}" + REGISTRY_SUFFIX;
  private static final String STANDALONE_ENTRY_PREFIX = GEO_PREFIX + ":";
  private static final String CLUSTER_ENTRY_PREFIX = GEO_PREFIX + ":{";
  private static final String CLUSTER_ENTRY_SUFFIX = "}" + REGISTRY_SUFFIX;

  private final RegistryKind registryKind;

  EventGeoCacheKeyLayout(RegistryKind registryKind) {
    this.registryKind = registryKind;
  }

  /** How this layout records which sources currently hold live geo state. */
  public RegistryKind registryKind() {
    return registryKind;
  }

  /** Builds the complete key aggregate for a normal UUID source id. */
  public SourceKeys sourceKeys(UUID sourceId) {
    return sourceKeys(Objects.requireNonNull(sourceId, "sourceId").toString());
  }

  /**
   * Builds the complete key aggregate for a source id.
   *
   * <p>{@link #STANDALONE} formats any nonnull id verbatim so the legacy facade keeps its exact
   * historical output. {@link #CLUSTER} rejects an empty id and any id carrying {@code '{'}, {@code
   * '}'}, {@code ':'}, or a Redis glob metacharacter, because those characters would make the hash
   * tag or directory-entry parsing ambiguous.
   */
  public SourceKeys sourceKeys(String sourceId) {
    Objects.requireNonNull(sourceId, "sourceId");
    assertConstructableSourceId(sourceId);
    var sourcePrefix = sourcePrefix(sourceId);
    return new SourceKeys(
        this,
        sourceId,
        sourcePrefix,
        eventPrefixFromSourcePrefix(sourcePrefix),
        updatedAtFromSourcePrefix(sourcePrefix),
        staleAtFromSourcePrefix(sourcePrefix),
        hydrationLockFromSourcePrefix(sourcePrefix),
        registryKeyFromSourcePrefix(sourcePrefix));
  }

  /**
   * Parses one stored directory entry back into its canonical aggregate.
   *
   * <p>A {@link #STANDALONE} entry is a source prefix stored as a member of the global sorted set.
   * A {@link #CLUSTER} entry is a persistent marker key name. Anything else is rejected rather than
   * guessed at, so a corrupt or foreign entry can never name a real source.
   */
  public SourceKeys sourceKeysFromDirectoryEntry(String entry) {
    Objects.requireNonNull(entry, "entry");
    return sourceKeys(sourceIdFromDirectoryEntry(entry));
  }

  /** The {@code SCAN} pattern matching every persistent cluster source marker. */
  public String markerScanPattern() {
    return switch (this) {
      case STANDALONE ->
          throw new IllegalStateException(
              "STANDALONE discovers sources through " + GLOBAL_SOURCE_INDEX + ", not marker keys");
      case CLUSTER -> CLUSTER_MARKER_SCAN_PATTERN;
    };
  }

  /** Appends the update-index suffix to any nonnull source prefix. */
  public String updatedAtFromSourcePrefix(String sourcePrefix) {
    return Objects.requireNonNull(sourcePrefix, "sourcePrefix") + UPDATED_AT_SUFFIX;
  }

  /** Appends the stale-index suffix to any nonnull source prefix. */
  public String staleAtFromSourcePrefix(String sourcePrefix) {
    return Objects.requireNonNull(sourcePrefix, "sourcePrefix") + STALE_AT_SUFFIX;
  }

  /** Appends the event-prefix suffix to any nonnull source prefix. */
  public String eventPrefixFromSourcePrefix(String sourcePrefix) {
    return Objects.requireNonNull(sourcePrefix, "sourcePrefix") + EVENT_SUFFIX;
  }

  private String hydrationLockFromSourcePrefix(String sourcePrefix) {
    return sourcePrefix + HYDRATION_LOCK_SUFFIX;
  }

  private String sourcePrefix(String sourceId) {
    return switch (this) {
      case STANDALONE -> GEO_PREFIX + ":" + sourceId;
      case CLUSTER -> GEO_PREFIX + ":{" + sourceId + "}";
    };
  }

  private String registryKeyFromSourcePrefix(String sourcePrefix) {
    return switch (this) {
      case STANDALONE -> GLOBAL_SOURCE_INDEX;
      case CLUSTER -> sourcePrefix + REGISTRY_SUFFIX;
    };
  }

  private String sourceIdFromDirectoryEntry(String entry) {
    return switch (this) {
      case STANDALONE -> {
        if (!entry.startsWith(STANDALONE_ENTRY_PREFIX)) {
          throw noncanonicalEntry(entry);
        }
        yield requireCanonicalEntrySourceId(
            entry.substring(STANDALONE_ENTRY_PREFIX.length()), entry);
      }
      case CLUSTER -> {
        if (!entry.startsWith(CLUSTER_ENTRY_PREFIX) || !entry.endsWith(CLUSTER_ENTRY_SUFFIX)) {
          throw noncanonicalEntry(entry);
        }
        var start = CLUSTER_ENTRY_PREFIX.length();
        var end = entry.length() - CLUSTER_ENTRY_SUFFIX.length();
        if (end < start) {
          throw noncanonicalEntry(entry);
        }
        yield requireCanonicalEntrySourceId(entry.substring(start, end), entry);
      }
    };
  }

  /**
   * Validates a source id recovered from a stored directory entry.
   *
   * <p>{@link #STANDALONE} accepts every suffix after its required prefix because construction must
   * format every nonnull id verbatim for legacy compatibility. A rejected standalone entry is
   * treated as corrupt and deleted, so narrowing the parser beyond construction would destroy a
   * registration that this same layout could legitimately have produced. {@link #CLUSTER} remains
   * strict because braces, colons, and Redis glob metacharacters would make its marker names or
   * later source-scoped scan patterns ambiguous.
   */
  private String requireCanonicalEntrySourceId(String sourceId, String entry) {
    if (this == STANDALONE) {
      return sourceId;
    }
    if (sourceId.isEmpty()
        || containsBrace(sourceId)
        || containsGlobMetacharacter(sourceId)
        || sourceId.indexOf(':') >= 0) {
      throw noncanonicalEntry(entry);
    }
    return sourceId;
  }

  private void assertConstructableSourceId(String sourceId) {
    if (this == STANDALONE) {
      return;
    }
    if (sourceId.isEmpty()) {
      throw new IllegalArgumentException("CLUSTER source id must not be empty");
    }
    if (containsBrace(sourceId) || sourceId.indexOf(':') >= 0) {
      throw new IllegalArgumentException(
          "CLUSTER source id must not contain '{', '}' or ':' but was " + sourceId);
    }
    if (containsGlobMetacharacter(sourceId)) {
      throw new IllegalArgumentException(
          "CLUSTER source id must not contain a glob metacharacter but was " + sourceId);
    }
  }

  private IllegalArgumentException noncanonicalEntry(String entry) {
    return new IllegalArgumentException(
        "Noncanonical " + name() + " geo directory entry: " + entry);
  }

  private static boolean containsBrace(String sourceId) {
    return sourceId.indexOf('{') >= 0 || sourceId.indexOf('}') >= 0;
  }

  /**
   * Rejects Redis glob metacharacters in cluster source ids.
   *
   * <p>Without this, {@code opencop:event:geo:{*}:registry} — the literal marker scan pattern —
   * would parse as a source whose {@link SourceKeys#eventScanPattern()} matches every other
   * source's payload keys, so one planted or mistaken key could widen a later source-scoped delete
   * into a cross-source one.
   */
  private static boolean containsGlobMetacharacter(String sourceId) {
    for (var index = 0; index < sourceId.length(); index++) {
      switch (sourceId.charAt(index)) {
        case '*', '?', '[', ']', '\\' -> {
          return true;
        }
        default -> {
          // Ordinary identifier character.
        }
      }
    }
    return false;
  }

  /** How a layout records which sources currently hold live geo state. */
  public enum RegistryKind {

    /** One global sorted set whose members are source prefixes. */
    GLOBAL_SORTED_SET,

    /** One persistent marker key co-located with each source's own keys. */
    PER_SOURCE_MARKER;

    /** Exact token passed to Lua when a script must branch on registry shape. */
    public String wireValue() {
      return name();
    }
  }

  /**
   * Immutable, self-validating key aggregate for exactly one source in exactly one layout.
   *
   * <p>The compact constructor recomputes every key from {@code layout} and {@code sourceId}, so no
   * caller can assemble a mixed or hand-edited aggregate.
   */
  public record SourceKeys(
      EventGeoCacheKeyLayout layout,
      String sourceId,
      String sourcePrefix,
      String eventPrefix,
      String updatedAtKey,
      String staleAtKey,
      String hydrationLockKey,
      String registryKey) {

    public SourceKeys {
      Objects.requireNonNull(layout, "layout");
      Objects.requireNonNull(sourceId, "sourceId");
      Objects.requireNonNull(sourcePrefix, "sourcePrefix");
      Objects.requireNonNull(eventPrefix, "eventPrefix");
      Objects.requireNonNull(updatedAtKey, "updatedAtKey");
      Objects.requireNonNull(staleAtKey, "staleAtKey");
      Objects.requireNonNull(hydrationLockKey, "hydrationLockKey");
      Objects.requireNonNull(registryKey, "registryKey");
      layout.assertConstructableSourceId(sourceId);
      var canonicalPrefix = layout.sourcePrefix(sourceId);
      requireCanonical("sourcePrefix", sourcePrefix, canonicalPrefix);
      requireCanonical(
          "eventPrefix", eventPrefix, layout.eventPrefixFromSourcePrefix(canonicalPrefix));
      requireCanonical(
          "updatedAtKey", updatedAtKey, layout.updatedAtFromSourcePrefix(canonicalPrefix));
      requireCanonical("staleAtKey", staleAtKey, layout.staleAtFromSourcePrefix(canonicalPrefix));
      requireCanonical(
          "hydrationLockKey",
          hydrationLockKey,
          layout.hydrationLockFromSourcePrefix(canonicalPrefix));
      requireCanonical(
          "registryKey", registryKey, layout.registryKeyFromSourcePrefix(canonicalPrefix));
    }

    /** How this source's membership is recorded. */
    public RegistryKind registryKind() {
      return layout.registryKind();
    }

    /** The payload key for one object inside this source. */
    public String eventKey(String objectId) {
      return eventPrefix + ":" + Objects.requireNonNull(objectId, "objectId");
    }

    /** The {@code SCAN} pattern matching every payload key inside this source. */
    public String eventScanPattern() {
      return eventPrefix + ":*";
    }

    private static void requireCanonical(String field, String actual, String expected) {
      if (!actual.equals(expected)) {
        throw new IllegalArgumentException(
            "EventGeoCacheKeyLayout.SourceKeys."
                + field
                + " must be "
                + expected
                + " but was "
                + actual);
      }
    }
  }
}
