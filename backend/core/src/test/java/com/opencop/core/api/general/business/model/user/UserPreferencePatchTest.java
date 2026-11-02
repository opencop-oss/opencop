// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserPreferencePatchTest {

  @Test
  void avatarOnly_withId_setsAvatarAndLeavesOthersEmpty() {
    var id = UUID.randomUUID();
    var patch = UserPreferencePatch.avatarOnly(id);

    assertEquals(Optional.of(id), patch.avatarMediaId());
    assertTrue(patch.username().isEmpty());
    assertTrue(patch.locale().isEmpty());
    assertTrue(patch.measurementSystem().isEmpty());
    assertTrue(patch.temperatureUnit().isEmpty());
    assertTrue(patch.timezone().isEmpty());
    assertTrue(patch.zoomBehavior().isEmpty());
    assertTrue(patch.initialView().isEmpty());
    assertTrue(patch.defaultTileId().isEmpty());
  }

  @Test
  void avatarOnly_withNull_setsAvatarToEmptyOptional() {
    // Optional.empty() means "set to null" (clear the avatar)
    var patch = UserPreferencePatch.avatarOnly(null);

    // Must be Optional.empty() (not a null reference) — this is "set to null"
    assertEquals(Optional.empty(), patch.avatarMediaId());
    assertTrue(patch.username().isEmpty());
    assertTrue(patch.locale().isEmpty());
  }

  @Test
  void withoutAvatar_stripsAvatarAndPreservesOtherFields() {
    var id = UUID.randomUUID();
    var patch = UserPreferencePatch.avatarOnly(id);
    var stripped = patch.withoutAvatar();

    // avatarMediaId must be null reference (not Optional.empty()) — this is "don't touch"
    assertNull(stripped.avatarMediaId());
    assertTrue(stripped.username().isEmpty());
    // Other fields preserved (were Optional.empty() from avatarOnly)
    assertTrue(stripped.locale().isEmpty());
  }

  @Test
  void constructor_nullAvatarMediaId_staysNull() {
    // When avatarMediaId is passed as null, it must NOT be normalized to Optional.empty().
    // null reference = "don't touch" vs Optional.empty() = "set to null"
    var patch =
        new UserPreferencePatch(
            Optional.of("user"), Optional.of("en"), null, null, null, null, null, null, null);

    // Other fields get normalized by compact constructor
    assertEquals(Optional.of("user"), patch.username());
    assertTrue(patch.measurementSystem().isEmpty());
    // avatarMediaId stays raw null — intentionally not normalized
    assertNull(patch.avatarMediaId());
  }

  @Test
  void withoutAvatar_preservesAllOtherFields() {
    var viewId = UUID.randomUUID();
    var tileId = UUID.randomUUID();
    var avatarId = UUID.randomUUID();
    var patch =
        new UserPreferencePatch(
            Optional.of("user"),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("UTC"),
            Optional.empty(),
            Optional.of(viewId),
            Optional.of(tileId),
            Optional.of(avatarId));

    var stripped = patch.withoutAvatar();

    assertEquals(Optional.of("user"), stripped.username());
    assertEquals(Optional.of("fr"), stripped.locale());
    assertEquals(Optional.of("UTC"), stripped.timezone());
    assertEquals(Optional.of(viewId), stripped.initialView());
    assertEquals(Optional.of(tileId), stripped.defaultTileId());
    assertNull(stripped.avatarMediaId());
  }
}
