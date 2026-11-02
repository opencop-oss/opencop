// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.general.business.model.Caller;
import java.util.UUID;

/**
 * Actors for tests that care who acted but not what their name was.
 *
 * <p>Deliberately test-only. Production code builds callers through {@code Caller.of}, which copies
 * the caller's real name, email and admin state; a shortcut like this on the production type would
 * make it easy to record an action against an actor with none of those facts.
 */
public final class TestActors {

  private TestActors() {}

  /** A non-administrator actor with no name or email. */
  public static Caller of(UUID userId) {
    return new Caller(userId, null, null, false);
  }

  /** An actor that was a global administrator when it acted. */
  public static Caller admin(UUID userId) {
    return new Caller(userId, null, null, true);
  }
}
