// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Internal runtime classification of declared support, effective support after prerequisite
 * expansion, and explicit rejection. A resolved policy is exhaustive and disjoint and is not an
 * external serialization or management contract.
 */
public final class IntegrationCapabilityPolicy {
  private static final IntegrationCapabilityCatalog CAPABILITY_CATALOG =
      IntegrationCapabilityCatalog.standard();

  private final Set<IntegrationCapability> declaredSupported;
  private final Set<IntegrationCapability> effectiveSupported;
  private final Set<IntegrationCapability> rejected;

  private IntegrationCapabilityPolicy(
      Set<IntegrationCapability> declaredSupported,
      Set<IntegrationCapability> effectiveSupported,
      Set<IntegrationCapability> rejected) {
    this.declaredSupported = immutableCopy(declaredSupported);
    this.effectiveSupported = immutableCopy(effectiveSupported);
    this.rejected = immutableCopy(rejected);
  }

  public Set<IntegrationCapability> declaredSupported() {
    return declaredSupported;
  }

  public Set<IntegrationCapability> effectiveSupported() {
    return effectiveSupported;
  }

  public Set<IntegrationCapability> rejected() {
    return rejected;
  }

  public static IntegrationCapabilityPolicy resolve(
      Set<IntegrationCapability> declaredSupported, Set<IntegrationCapability> rejected) {
    var declaredCopy = mutableCopy(declaredSupported);
    var rejectedCopy = mutableCopy(rejected);
    var effective = mutableCopy(CAPABILITY_CATALOG.expandWithPrerequisites(declaredCopy));

    var overlap = mutableCopy(effective);
    overlap.retainAll(rejectedCopy);
    if (!overlap.isEmpty()) {
      throw new IllegalArgumentException(
          "Effective supported and explicitly rejected capabilities overlap: " + overlap);
    }

    var classified = mutableCopy(effective);
    classified.addAll(rejectedCopy);
    var omitted = EnumSet.allOf(IntegrationCapability.class);
    omitted.removeAll(classified);
    if (!omitted.isEmpty()) {
      throw new IllegalArgumentException(
          "Every integration capability must be classified: " + omitted);
    }

    return new IntegrationCapabilityPolicy(declaredCopy, effective, rejectedCopy);
  }

  private static Set<IntegrationCapability> immutableCopy(Set<IntegrationCapability> source) {
    var copy = mutableCopy(source);
    return copy.isEmpty() ? Set.of() : Collections.unmodifiableSet(copy);
  }

  private static EnumSet<IntegrationCapability> mutableCopy(Set<IntegrationCapability> source) {
    if (source == null) {
      throw new NullPointerException("Capability sets must not be null");
    }
    return source.isEmpty() ? EnumSet.noneOf(IntegrationCapability.class) : EnumSet.copyOf(source);
  }
}
