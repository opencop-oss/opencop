// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventGeoCacheKeysTest {

  private static final UUID GEO_SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final String PREFIX = "opencop:event:geo:550e8400-e29b-41d4-a716-446655440001";

  @Test
  void keyContract_matchesExistingRedisLayout() {
    assertEquals("opencop:event:geo", EventGeoCacheKeys.GEO_PREFIX);
    assertEquals("opencop:event:sources", EventGeoCacheKeys.SOURCE_INDEX);
    assertEquals(PREFIX, EventGeoCacheKeys.geoSourcePrefix(GEO_SOURCE_ID));
    assertEquals("%s:updatedAt".formatted(PREFIX), EventGeoCacheKeys.updatedAt(GEO_SOURCE_ID));
    assertEquals("%s:staleAt".formatted(PREFIX), EventGeoCacheKeys.staleAt(GEO_SOURCE_ID));
    assertEquals(
        "%s:event:object-1".formatted(PREFIX), EventGeoCacheKeys.event(GEO_SOURCE_ID, "object-1"));
    assertEquals("%s:event".formatted(PREFIX), EventGeoCacheKeys.eventPrefix(GEO_SOURCE_ID));
    assertEquals("%s:event:*".formatted(PREFIX), EventGeoCacheKeys.eventScanPattern(GEO_SOURCE_ID));
    assertEquals(
        "%s:hydrate:lock".formatted(PREFIX), EventGeoCacheKeys.hydrationLock(GEO_SOURCE_ID));
  }

  @Test
  void prefixHelpers_useStoredSourceIndexMembers() {
    assertEquals("%s:updatedAt".formatted(PREFIX), EventGeoCacheKeys.updatedAtFromPrefix(PREFIX));
    assertEquals("%s:staleAt".formatted(PREFIX), EventGeoCacheKeys.staleAtFromPrefix(PREFIX));
    assertEquals(
        "%s:event".formatted(PREFIX), EventGeoCacheKeys.eventPrefixFromGeoSourcePrefix(PREFIX));
  }

  @Test
  void stringSourceOverloads_matchUuidOverloads() {
    var source = GEO_SOURCE_ID.toString();

    assertEquals(
        EventGeoCacheKeys.geoSourcePrefix(GEO_SOURCE_ID),
        EventGeoCacheKeys.geoSourcePrefix(source));
    assertEquals(EventGeoCacheKeys.updatedAt(GEO_SOURCE_ID), EventGeoCacheKeys.updatedAt(source));
    assertEquals(EventGeoCacheKeys.staleAt(GEO_SOURCE_ID), EventGeoCacheKeys.staleAt(source));
    assertEquals(
        EventGeoCacheKeys.event(GEO_SOURCE_ID, "object-1"),
        EventGeoCacheKeys.event(source, "object-1"));
    assertEquals(
        EventGeoCacheKeys.eventPrefix(GEO_SOURCE_ID), EventGeoCacheKeys.eventPrefix(source));
    assertEquals(
        EventGeoCacheKeys.eventScanPattern(GEO_SOURCE_ID),
        EventGeoCacheKeys.eventScanPattern(source));
  }
}
