// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

import com.opencop.core.sdk.business.model.integration.StableKeySyntax;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable SDK capability catalog that validates completeness, stable keys, unique contribution
 * interface ownership, and prerequisite cycles once. It hides graph traversal from callers and
 * returns precomputed immutable prerequisite expansions.
 */
public final class IntegrationCapabilityCatalog {

  private final Map<IntegrationCapability, IntegrationCapabilityContract> contracts;
  private final Map<IntegrationCapability, Set<IntegrationCapability>> prerequisiteClosures;

  IntegrationCapabilityCatalog(Collection<IntegrationCapabilityContract> contracts) {
    var contractsByCapability = indexContracts(contracts);
    validateCompleteCatalog(contractsByCapability);
    validateStableKeys(contractsByCapability);
    validateContributionOwnership(contractsByCapability);
    this.prerequisiteClosures = computePrerequisiteClosures(contractsByCapability);
    this.contracts = Collections.unmodifiableMap(contractsByCapability);
  }

  public static IntegrationCapabilityCatalog standard() {
    return StandardCatalogHolder.INSTANCE;
  }

  public IntegrationCapabilityContract contractFor(IntegrationCapability capability) {
    if (capability == null) {
      throw new NullPointerException("Capability must not be null");
    }
    return contracts.get(capability);
  }

  /** Returns the supplied capabilities plus every direct and transitive prerequisite. */
  public Set<IntegrationCapability> expandWithPrerequisites(
      Set<IntegrationCapability> capabilities) {
    var expanded = mutableCopy(capabilities);
    for (var capability : capabilities) {
      expanded.addAll(prerequisiteClosures.get(capability));
    }
    return immutableCopy(expanded);
  }

  private static EnumMap<IntegrationCapability, IntegrationCapabilityContract> indexContracts(
      Collection<IntegrationCapabilityContract> contracts) {
    if (contracts == null) {
      throw new NullPointerException("Capability contracts must not be null");
    }
    var indexed =
        new EnumMap<IntegrationCapability, IntegrationCapabilityContract>(
            IntegrationCapability.class);
    for (var contract : contracts) {
      if (contract == null) {
        throw new NullPointerException("Capability contracts must not contain null");
      }
      if (indexed.putIfAbsent(contract.capability(), contract) != null) {
        throw new IllegalArgumentException(
            "The capability catalog must define each contract exactly once: "
                + contract.capability());
      }
    }
    return indexed;
  }

  private static void validateStableKeys(
      Map<IntegrationCapability, IntegrationCapabilityContract> contracts) {
    var ownersByStableKey = new HashMap<String, IntegrationCapability>();
    for (var contract : contracts.values()) {
      if (!StableKeySyntax.isValid(contract.stableKey())) {
        throw new IllegalArgumentException(
            "Capability "
                + contract.capability()
                + " has invalid stable key '"
                + contract.stableKey()
                + "'; stable keys must use "
                + StableKeySyntax.DESCRIPTION);
      }
      var priorOwner = ownersByStableKey.putIfAbsent(contract.stableKey(), contract.capability());
      if (priorOwner != null) {
        throw new IllegalArgumentException(
            "Capability stable key '"
                + contract.stableKey()
                + "' is owned by both "
                + priorOwner
                + " and "
                + contract.capability());
      }
    }
  }

  private static void validateCompleteCatalog(
      Map<IntegrationCapability, IntegrationCapabilityContract> contracts) {
    var missingCapabilities = EnumSet.allOf(IntegrationCapability.class);
    missingCapabilities.removeAll(contracts.keySet());
    if (!missingCapabilities.isEmpty()) {
      throw new IllegalArgumentException(
          "The capability catalog is missing contracts for: " + missingCapabilities);
    }
  }

  private static void validateContributionOwnership(
      Map<IntegrationCapability, IntegrationCapabilityContract> contracts) {
    var owners = new HashMap<Class<?>, IntegrationCapability>();
    for (var contract : contracts.values()) {
      for (var contributionInterface : contract.contributionInterfaces()) {
        var priorOwner = owners.putIfAbsent(contributionInterface, contract.capability());
        if (priorOwner != null) {
          throw new IllegalArgumentException(
              "Contribution interfaces must be owned by exactly one capability: "
                  + contributionInterface.getName());
        }
      }
    }
  }

  private static Map<IntegrationCapability, Set<IntegrationCapability>> computePrerequisiteClosures(
      Map<IntegrationCapability, IntegrationCapabilityContract> contracts) {
    var closures =
        new EnumMap<IntegrationCapability, Set<IntegrationCapability>>(IntegrationCapability.class);
    for (var capability : IntegrationCapability.values()) {
      computePrerequisiteClosure(capability, contracts, closures, new ArrayDeque<>());
    }
    return Collections.unmodifiableMap(closures);
  }

  private static Set<IntegrationCapability> computePrerequisiteClosure(
      IntegrationCapability capability,
      Map<IntegrationCapability, IntegrationCapabilityContract> contracts,
      Map<IntegrationCapability, Set<IntegrationCapability>> closures,
      ArrayDeque<IntegrationCapability> path) {
    var cached = closures.get(capability);
    if (cached != null) {
      return cached;
    }
    if (path.contains(capability)) {
      throw prerequisiteCycle(path, capability);
    }

    path.addLast(capability);
    var closure = EnumSet.noneOf(IntegrationCapability.class);
    for (var prerequisite : contracts.get(capability).prerequisites()) {
      closure.addAll(computePrerequisiteClosure(prerequisite, contracts, closures, path));
      closure.add(prerequisite);
    }
    path.removeLast();

    var immutableClosure = immutableCopy(closure);
    closures.put(capability, immutableClosure);
    return immutableClosure;
  }

  private static IllegalArgumentException prerequisiteCycle(
      ArrayDeque<IntegrationCapability> path, IntegrationCapability repeated) {
    var cycle = new ArrayList<IntegrationCapability>();
    var recording = false;
    for (var capability : path) {
      if (capability == repeated) {
        recording = true;
      }
      if (recording) {
        cycle.add(capability);
      }
    }
    cycle.add(repeated);
    var rendered = cycle.stream().map(Enum::name).collect(Collectors.joining(" -> "));
    return new IllegalArgumentException(
        "Integration capability prerequisites contain a cycle: " + rendered);
  }

  private static Set<IntegrationCapability> immutableCopy(Set<IntegrationCapability> source) {
    return source.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(source));
  }

  private static EnumSet<IntegrationCapability> mutableCopy(Set<IntegrationCapability> source) {
    if (source == null) {
      throw new NullPointerException("Capability sets must not be null");
    }
    return source.isEmpty() ? EnumSet.noneOf(IntegrationCapability.class) : EnumSet.copyOf(source);
  }

  private static final class StandardCatalogHolder {
    private static final IntegrationCapabilityCatalog INSTANCE =
        IntegrationCapabilityCatalogFactory.createStandard();
  }
}
