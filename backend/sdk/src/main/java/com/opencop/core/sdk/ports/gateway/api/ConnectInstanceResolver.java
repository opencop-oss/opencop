// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers available Connect service instances for horizontal scaling.
 *
 * <p>Implementations discover instances dynamically through worker health and provide assignment
 * logic for distributing connections across instances.
 */
public interface ConnectInstanceResolver {

  /** Get all available Connect instance IDs. */
  List<String> getAllInstanceIds();

  /**
   * Get Connect worker health records that can be used as ownership and PUSH delivery targets.
   *
   * <p>Implementations should exclude expired-stale instances and fast-stale instances whose
   * heartbeat has not advanced after the route freshness gate.
   */
  List<ConnectInstanceInfo> getRoutableInstances(Duration freshnessWait);

  /**
   * Get Connect worker health records, waiting until at least {@code minimumCount} workers are
   * route-fresh when supported.
   */
  List<ConnectInstanceInfo> getRoutableInstances(Duration freshnessWait, int minimumCount);

  /**
   * Get route-fresh Connect worker health records for the currently fresh registry.
   *
   * <p>Implementations should derive the expected worker count and route-fresh baseline from the
   * same registry snapshot, then return all route-fresh workers available by the timeout.
   */
  List<ConnectInstanceInfo> getRoutableInstancesForCurrentRegistry(Duration freshnessWait);

  /** Get all routable instance IDs. */
  default List<String> getRoutableInstanceIds(Duration freshnessWait) {
    return getRoutableInstances(freshnessWait).stream()
        .map(ConnectInstanceInfo::instanceId)
        .toList();
  }

  /**
   * Get the next routable instance ID for ownership assignment.
   *
   * <p>The default policy uses the least-loaded routable instance.
   */
  default String getNextRoutableInstanceForAssignment(Duration freshnessWait) {
    return getRoutableInstances(freshnessWait).stream()
        .min(Comparator.comparingInt(ConnectInstanceInfo::connectionCount))
        .map(ConnectInstanceInfo::instanceId)
        .orElseThrow(() -> new IllegalStateException("No routable Connect instances registered"));
  }

  /** Clear the Connect instance health registry so only new heartbeats satisfy readiness. */
  default void clearInstanceRegistry() {}
}
