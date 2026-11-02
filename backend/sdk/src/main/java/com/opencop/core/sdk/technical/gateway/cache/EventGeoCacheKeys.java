// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache;

import java.util.Objects;
import java.util.UUID;

/**
 * Exact standalone compatibility facade over {@link EventGeoCacheKeyLayout#STANDALONE}.
 *
 * <p>{@link EventGeoCacheKeyLayout} is the implementation authority for live-geo key strings. This
 * class keeps every historical signature, null-rejection boundary, and returned string so existing
 * callers stay source- and state-compatible until they are deliberately migrated to a selected
 * layout.
 */
public final class EventGeoCacheKeys {

  public static final String GEO_PREFIX = EventGeoCacheKeyLayout.GEO_PREFIX;
  public static final String SOURCE_INDEX = EventGeoCacheKeyLayout.GLOBAL_SOURCE_INDEX;

  private static final EventGeoCacheKeyLayout LAYOUT = EventGeoCacheKeyLayout.STANDALONE;

  private EventGeoCacheKeys() {}

  public static String geoSourcePrefix(UUID geoSourceId) {
    return geoSourcePrefix(geoSourceId.toString());
  }

  public static String geoSourcePrefix(String geoSourceId) {
    return LAYOUT.sourceKeys(Objects.requireNonNull(geoSourceId, "geoSourceId")).sourcePrefix();
  }

  public static String updatedAt(UUID geoSourceId) {
    return updatedAtFromPrefix(geoSourcePrefix(geoSourceId));
  }

  public static String updatedAt(String geoSourceId) {
    return updatedAtFromPrefix(geoSourcePrefix(geoSourceId));
  }

  public static String updatedAtFromPrefix(String geoSourcePrefix) {
    return LAYOUT.updatedAtFromSourcePrefix(
        Objects.requireNonNull(geoSourcePrefix, "geoSourcePrefix"));
  }

  public static String staleAt(UUID geoSourceId) {
    return staleAtFromPrefix(geoSourcePrefix(geoSourceId));
  }

  public static String staleAt(String geoSourceId) {
    return staleAtFromPrefix(geoSourcePrefix(geoSourceId));
  }

  public static String staleAtFromPrefix(String geoSourcePrefix) {
    return LAYOUT.staleAtFromSourcePrefix(
        Objects.requireNonNull(geoSourcePrefix, "geoSourcePrefix"));
  }

  public static String event(UUID geoSourceId, String objectId) {
    return event(geoSourceId.toString(), objectId);
  }

  public static String event(String geoSourceId, String objectId) {
    return LAYOUT.sourceKeys(Objects.requireNonNull(geoSourceId, "geoSourceId")).eventKey(objectId);
  }

  public static String eventPrefix(UUID geoSourceId) {
    return eventPrefixFromGeoSourcePrefix(geoSourcePrefix(geoSourceId));
  }

  public static String eventPrefix(String geoSourceId) {
    return eventPrefixFromGeoSourcePrefix(geoSourcePrefix(geoSourceId));
  }

  public static String eventPrefixFromGeoSourcePrefix(String geoSourcePrefix) {
    return LAYOUT.eventPrefixFromSourcePrefix(
        Objects.requireNonNull(geoSourcePrefix, "geoSourcePrefix"));
  }

  public static String eventScanPattern(UUID geoSourceId) {
    return eventScanPattern(geoSourceId.toString());
  }

  public static String eventScanPattern(String geoSourceId) {
    return LAYOUT.sourceKeys(Objects.requireNonNull(geoSourceId, "geoSourceId")).eventScanPattern();
  }

  public static String hydrationLock(UUID geoSourceId) {
    return LAYOUT.sourceKeys(geoSourceId.toString()).hydrationLockKey();
  }
}
