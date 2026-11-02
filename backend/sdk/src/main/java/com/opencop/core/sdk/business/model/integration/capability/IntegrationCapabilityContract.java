// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

import java.util.Set;

/**
 * States one capability's stable resource key, direct prerequisites, and uniquely owned
 * contribution interfaces. Each interface permits any number of statically typed registrations;
 * registration cardinality does not define consumption cardinality, which belongs to each host
 * consumer's contract.
 */
public record IntegrationCapabilityContract(
    IntegrationCapability capability,
    String stableKey,
    Set<IntegrationCapability> prerequisites,
    Set<Class<?>> contributionInterfaces) {

  public IntegrationCapabilityContract {
    if (capability == null) {
      throw new NullPointerException("Capability must not be null");
    }
    if (stableKey == null) {
      throw new NullPointerException("Capability stable key must not be null");
    }
    prerequisites = Set.copyOf(prerequisites);
    contributionInterfaces = Set.copyOf(contributionInterfaces);
    if (prerequisites.contains(capability)) {
      throw new IllegalArgumentException("A capability cannot require itself");
    }
    if (contributionInterfaces.isEmpty()) {
      throw new IllegalArgumentException(
          "A capability contract requires at least one contribution interface");
    }
    for (var contributionInterface : contributionInterfaces) {
      if (!contributionInterface.isInterface()) {
        throw new IllegalArgumentException(
            "Capability contribution types must be interfaces: " + contributionInterface.getName());
      }
    }
  }
}
