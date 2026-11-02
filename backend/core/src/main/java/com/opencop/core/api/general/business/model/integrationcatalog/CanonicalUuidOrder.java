// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Comparator;
import java.util.UUID;

/** The order a published identifier list follows, so a client can reproduce it from the text. */
public final class CanonicalUuidOrder {

  /**
   * Orders identifiers by their canonical text rather than by {@link UUID#compareTo}, which
   * compares the two halves as signed longs and so disagrees with the text a client sorts on.
   */
  public static final Comparator<UUID> BY_CANONICAL_TEXT = Comparator.comparing(UUID::toString);

  private CanonicalUuidOrder() {}
}
