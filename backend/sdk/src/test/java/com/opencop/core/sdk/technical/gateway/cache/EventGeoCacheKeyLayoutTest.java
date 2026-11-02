// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.RegistryKind;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import io.lettuce.core.cluster.SlotHash;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventGeoCacheKeyLayoutTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final String STANDALONE_PREFIX =
      "opencop:event:geo:550e8400-e29b-41d4-a716-446655440001";
  private static final String CLUSTER_PREFIX =
      "opencop:event:geo:{550e8400-e29b-41d4-a716-446655440001}";
  private static final List<StandaloneFixture> UNUSUAL_STANDALONE_FIXTURES =
      List.of(
          new StandaloneFixture("", "opencop:event:geo:"),
          new StandaloneFixture("{braced}", "opencop:event:geo:{braced}"),
          new StandaloneFixture("with:colon", "opencop:event:geo:with:colon"),
          new StandaloneFixture(" space ", "opencop:event:geo: space "),
          new StandaloneFixture("*", "opencop:event:geo:*"),
          new StandaloneFixture("a?b", "opencop:event:geo:a?b"),
          new StandaloneFixture("a[0-9]", "opencop:event:geo:a[0-9]"),
          new StandaloneFixture("a\\b", "opencop:event:geo:a\\b"));

  @Test
  void standaloneLayout_returnsTheExactLegacyKeysAndGlobalRegistry() {
    var keys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);

    assertThat(keys.layout()).isEqualTo(EventGeoCacheKeyLayout.STANDALONE);
    assertThat(keys.sourceId()).isEqualTo(SOURCE_ID.toString());
    assertThat(keys.sourcePrefix()).isEqualTo(STANDALONE_PREFIX);
    assertThat(keys.eventPrefix()).isEqualTo(STANDALONE_PREFIX + ":event");
    assertThat(keys.eventKey("object-1")).isEqualTo(STANDALONE_PREFIX + ":event:object-1");
    assertThat(keys.eventScanPattern()).isEqualTo(STANDALONE_PREFIX + ":event:*");
    assertThat(keys.updatedAtKey()).isEqualTo(STANDALONE_PREFIX + ":updatedAt");
    assertThat(keys.staleAtKey()).isEqualTo(STANDALONE_PREFIX + ":staleAt");
    assertThat(keys.hydrationLockKey()).isEqualTo(STANDALONE_PREFIX + ":hydrate:lock");
    assertThat(keys.registryKey()).isEqualTo("opencop:event:sources");
    assertThat(keys.registryKind()).isEqualTo(RegistryKind.GLOBAL_SORTED_SET);
    assertThat(EventGeoCacheKeyLayout.STANDALONE.registryKind().wireValue())
        .isEqualTo("GLOBAL_SORTED_SET");
    assertThat(EventGeoCacheKeyLayout.CLUSTER.registryKind().wireValue())
        .isEqualTo("PER_SOURCE_MARKER");
    assertThat(keys).isEqualTo(EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID));
  }

  @Test
  void standaloneLayout_formatsUnusualIdentifiersWithTheExactLegacyGrammar() {
    for (var fixture : UNUSUAL_STANDALONE_FIXTURES) {
      var sourceId = fixture.sourceId();
      var expectedPrefix = fixture.expectedPrefix();
      var keys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(sourceId);

      assertThat(keys.sourceId()).isEqualTo(sourceId);
      assertThat(keys.sourcePrefix()).isEqualTo(expectedPrefix);
      assertThat(keys.updatedAtKey()).isEqualTo(expectedPrefix + ":updatedAt");
      assertThat(keys.staleAtKey()).isEqualTo(expectedPrefix + ":staleAt");
      assertThat(keys.eventPrefix()).isEqualTo(expectedPrefix + ":event");
      assertThat(keys.eventScanPattern()).isEqualTo(expectedPrefix + ":event:*");
      assertThat(keys.eventKey("object-1")).isEqualTo(expectedPrefix + ":event:object-1");
      assertThat(keys.registryKey()).isEqualTo("opencop:event:sources");

      assertThat(EventGeoCacheKeys.geoSourcePrefix(sourceId)).isEqualTo(expectedPrefix);
      assertThat(EventGeoCacheKeys.updatedAt(sourceId)).isEqualTo(expectedPrefix + ":updatedAt");
      assertThat(EventGeoCacheKeys.staleAt(sourceId)).isEqualTo(expectedPrefix + ":staleAt");
      assertThat(EventGeoCacheKeys.eventPrefix(sourceId)).isEqualTo(expectedPrefix + ":event");
      assertThat(EventGeoCacheKeys.eventScanPattern(sourceId))
          .isEqualTo(expectedPrefix + ":event:*");
      assertThat(EventGeoCacheKeys.event(sourceId, "object-1"))
          .isEqualTo(expectedPrefix + ":event:object-1");
      assertThat(EventGeoCacheKeys.SOURCE_INDEX).isEqualTo("opencop:event:sources");
    }

    var arbitraryPrefix = "totally:arbitrary:prefix";
    assertThat(EventGeoCacheKeyLayout.STANDALONE.updatedAtFromSourcePrefix(arbitraryPrefix))
        .isEqualTo(EventGeoCacheKeys.updatedAtFromPrefix(arbitraryPrefix));
    assertThat(EventGeoCacheKeyLayout.STANDALONE.staleAtFromSourcePrefix(arbitraryPrefix))
        .isEqualTo(EventGeoCacheKeys.staleAtFromPrefix(arbitraryPrefix));
    assertThat(EventGeoCacheKeyLayout.STANDALONE.eventPrefixFromSourcePrefix(arbitraryPrefix))
        .isEqualTo(EventGeoCacheKeys.eventPrefixFromGeoSourcePrefix(arbitraryPrefix));
  }

  @Test
  void clusterLayout_tagsEverySourceLocalKeyIntoOneSlot() {
    var keys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_ID);

    assertThat(keys.layout()).isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
    assertThat(keys.sourcePrefix()).isEqualTo(CLUSTER_PREFIX);
    assertThat(keys.eventPrefix()).isEqualTo(CLUSTER_PREFIX + ":event");
    assertThat(keys.eventKey("object-1")).isEqualTo(CLUSTER_PREFIX + ":event:object-1");
    assertThat(keys.eventScanPattern()).isEqualTo(CLUSTER_PREFIX + ":event:*");
    assertThat(keys.updatedAtKey()).isEqualTo(CLUSTER_PREFIX + ":updatedAt");
    assertThat(keys.staleAtKey()).isEqualTo(CLUSTER_PREFIX + ":staleAt");
    assertThat(keys.hydrationLockKey()).isEqualTo(CLUSTER_PREFIX + ":hydrate:lock");
    assertThat(keys.registryKey()).isEqualTo(CLUSTER_PREFIX + ":registry");
    assertThat(keys.registryKind()).isEqualTo(RegistryKind.PER_SOURCE_MARKER);
    assertThat(EventGeoCacheKeyLayout.CLUSTER.markerScanPattern())
        .isEqualTo("opencop:event:geo:{*}:registry");

    var slot = SlotHash.getSlot(keys.registryKey());
    assertThat(SlotHash.getSlot(keys.sourcePrefix())).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.eventPrefix())).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.eventKey("object-1"))).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.eventKey("object-2"))).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.updatedAtKey())).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.staleAtKey())).isEqualTo(slot);
    assertThat(SlotHash.getSlot(keys.hydrationLockKey())).isEqualTo(slot);

    var otherSource = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(UUID.randomUUID());
    assertThat(otherSource.registryKey()).isNotEqualTo(keys.registryKey());
    assertThat(EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID).sourcePrefix())
        .isNotEqualTo(keys.sourcePrefix());
  }

  @Test
  void directoryEntries_roundTripToTheirCanonicalSourceKeys() {
    var standalone = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    var cluster = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_ID);

    assertThat(
            EventGeoCacheKeyLayout.STANDALONE.sourceKeysFromDirectoryEntry(
                standalone.sourcePrefix()))
        .isEqualTo(standalone);
    assertThat(EventGeoCacheKeyLayout.CLUSTER.sourceKeysFromDirectoryEntry(cluster.registryKey()))
        .isEqualTo(cluster);

    assertThat(
            EventGeoCacheKeyLayout.STANDALONE.sourceKeysFromDirectoryEntry(cluster.registryKey()))
        .isEqualTo(EventGeoCacheKeyLayout.STANDALONE.sourceKeys("{" + SOURCE_ID + "}:registry"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                EventGeoCacheKeyLayout.CLUSTER.sourceKeysFromDirectoryEntry(
                    standalone.sourcePrefix()));

    // Discovery deletes rejected standalone entries, so parsing must be closed over construction.
    for (var fixture : UNUSUAL_STANDALONE_FIXTURES) {
      var keys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(fixture.sourceId());
      assertThat(
              EventGeoCacheKeyLayout.STANDALONE.sourceKeysFromDirectoryEntry(keys.sourcePrefix()))
          .isEqualTo(keys);
    }
  }

  @Test
  void malformedInput_isRejectedRatherThanGuessed() {
    assertThatNullPointerException()
        .isThrownBy(() -> EventGeoCacheKeyLayout.STANDALONE.sourceKeys((String) null));
    assertThatNullPointerException()
        .isThrownBy(() -> EventGeoCacheKeyLayout.STANDALONE.sourceKeys((UUID) null));
    assertThatNullPointerException()
        .isThrownBy(() -> EventGeoCacheKeyLayout.CLUSTER.sourceKeys((String) null));
    assertThatNullPointerException()
        .isThrownBy(() -> EventGeoCacheKeyLayout.CLUSTER.sourceKeysFromDirectoryEntry(null));
    assertThatNullPointerException()
        .isThrownBy(() -> EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID).eventKey(null));

    for (var rejected :
        List.of("", "{tagged}", "with:colon", "trailing}", "*", "a?b", "a[0-9]", "a\\b")) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventGeoCacheKeyLayout.CLUSTER.sourceKeys(rejected));
    }

    for (var entry : List.of("", "opencop:event:sources", "opencop:event", "unrelated:key")) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventGeoCacheKeyLayout.STANDALONE.sourceKeysFromDirectoryEntry(entry));
    }

    for (var entry :
        List.of(
            "",
            "opencop:event:geo:{}:registry",
            "opencop:event:geo:{550e8400}:updatedAt",
            "opencop:event:geo:550e8400:registry",
            "opencop:event:geo:{a}:registry:{b}:registry",
            "opencop:event:geo:{a:b}:registry",
            "unrelated:key")) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventGeoCacheKeyLayout.CLUSTER.sourceKeysFromDirectoryEntry(entry));
    }

    // The marker scan pattern must never parse as a source: its id would be the glob "*", whose
    // eventScanPattern() would match every other source's payload keys.
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                EventGeoCacheKeyLayout.CLUSTER.sourceKeysFromDirectoryEntry(
                    EventGeoCacheKeyLayout.CLUSTER.markerScanPattern()));

    assertThatIllegalStateException()
        .isThrownBy(EventGeoCacheKeyLayout.STANDALONE::markerScanPattern);
  }

  @Test
  void sourceKeys_cannotBeAssembledFromMixedOrTamperedFields() {
    var standalone = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    var cluster = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SOURCE_ID);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceKeys(
                    EventGeoCacheKeyLayout.CLUSTER,
                    standalone.sourceId(),
                    standalone.sourcePrefix(),
                    standalone.eventPrefix(),
                    standalone.updatedAtKey(),
                    standalone.staleAtKey(),
                    standalone.hydrationLockKey(),
                    standalone.registryKey()));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceKeys(
                    cluster.layout(),
                    cluster.sourceId(),
                    cluster.sourcePrefix(),
                    cluster.eventPrefix(),
                    cluster.updatedAtKey(),
                    cluster.staleAtKey(),
                    cluster.hydrationLockKey(),
                    "opencop:event:sources"));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceKeys(
                    standalone.layout(),
                    standalone.sourceId(),
                    standalone.sourcePrefix(),
                    standalone.eventPrefix(),
                    cluster.updatedAtKey(),
                    standalone.staleAtKey(),
                    standalone.hydrationLockKey(),
                    standalone.registryKey()));

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () ->
                new SourceKeys(
                    standalone.layout(),
                    standalone.sourceId(),
                    standalone.sourcePrefix(),
                    standalone.eventPrefix(),
                    standalone.updatedAtKey(),
                    standalone.staleAtKey(),
                    standalone.hydrationLockKey(),
                    null));
  }

  private record StandaloneFixture(String sourceId, String expectedPrefix) {}
}
