// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectServiceExceptionCodes;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves route-fresh Connect instances for ownership assignment and PUSH delivery. */
public class ConnectRoutingTargetResolver {

  private static final Logger log = LoggerFactory.getLogger(ConnectRoutingTargetResolver.class);
  private static final Duration ROUTING_FRESHNESS_WAIT = Duration.ofSeconds(20);

  private final ConnectInstanceResolver instanceResolver;

  public ConnectRoutingTargetResolver(ConnectInstanceResolver instanceResolver) {
    this.instanceResolver = instanceResolver;
  }

  public record OwnershipTarget(String instanceId, boolean assignmentChanged) {}

  public OwnershipTarget resolveOwnershipTarget(
      UUID sourceId, String assignedInstance, String logLabel) {
    return resolveOwnershipTarget(sourceId, assignedInstance, logLabel, routableInstances());
  }

  public OwnershipTarget resolveOwnershipTarget(
      UUID sourceId,
      String assignedInstance,
      String logLabel,
      List<ConnectInstanceInfo> routableInstances) {
    var target =
        new BatchOwnershipTargetSelector(routableInstances, List.of())
            .resolveOwnershipTarget(sourceId, assignedInstance, logLabel);
    if (!target.assignmentChanged()) {
      log.info(
          "{} for source {} using assigned Connect instance {}",
          logLabel,
          sourceId,
          assignedInstance);
    }
    return target;
  }

  public List<ConnectInstanceInfo> routableInstances() {
    return instanceResolver.getRoutableInstances(ROUTING_FRESHNESS_WAIT);
  }

  public List<ConnectInstanceInfo> batchRoutableInstances() {
    return instanceResolver.getRoutableInstancesForCurrentRegistry(ROUTING_FRESHNESS_WAIT);
  }

  public BatchOwnershipTargetSelector batchOwnershipTargetSelector(
      List<ConnectInstanceInfo> routableInstances, Collection<String> desiredAssignments) {
    return new BatchOwnershipTargetSelector(routableInstances, desiredAssignments);
  }

  public Optional<String> resolveKnownOwnerTarget(
      UUID sourceId, String assignedInstance, String logLabel) {
    if (assignedInstance == null) {
      log.info("{} for source {} skipped: no assigned Connect instance", logLabel, sourceId);
      return Optional.empty();
    }

    var routable = instanceResolver.getRoutableInstances(ROUTING_FRESHNESS_WAIT);
    var assignedIsRoutable =
        routable.stream().anyMatch(info -> assignedInstance.equals(info.instanceId()));

    if (assignedIsRoutable) {
      return Optional.of(assignedInstance);
    }

    log.info(
        "{} for source {} skipped stale assigned Connect instance {}",
        logLabel,
        sourceId,
        assignedInstance);
    return Optional.empty();
  }

  public static final class BatchOwnershipTargetSelector {

    private final Set<String> routableIds;
    private final Map<String, Integer> projectedLoad;

    private BatchOwnershipTargetSelector(
        List<ConnectInstanceInfo> routableInstances, Collection<String> desiredAssignments) {
      routableIds = new HashSet<>(routableInstances.size());
      for (var instance : routableInstances) {
        routableIds.add(instance.instanceId());
      }

      var desiredAssignmentCounts = new HashMap<String, Integer>();
      for (var assignment : desiredAssignments) {
        if (assignment != null && routableIds.contains(assignment)) {
          desiredAssignmentCounts.merge(assignment, 1, Integer::sum);
        }
      }

      projectedLoad = new HashMap<>(routableInstances.size());
      for (var instance : routableInstances) {
        projectedLoad.put(
            instance.instanceId(),
            Math.max(
                instance.connectionCount(),
                desiredAssignmentCounts.getOrDefault(instance.instanceId(), 0)));
      }
    }

    public OwnershipTarget resolveOwnershipTarget(
        UUID sourceId, String assignedInstance, String logLabel) {
      // In a future K8s StatefulSet, stable pod names can make assigned_instance survive Connect
      // restarts; reconciliation still corrects stale assignments.
      if (assignedInstance != null && routableIds.contains(assignedInstance)) {
        return new OwnershipTarget(assignedInstance, false);
      }

      var selected =
          projectedLoad.entrySet().stream()
              .min(
                  Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                      .thenComparing(Map.Entry::getKey))
              .orElseThrow(
                  () ->
                      new ConnectionManagementException(
                          ConnectServiceExceptionCodes.SERVICE_UNAVAILABLE));
      var selectedInstance = selected.getKey();
      projectedLoad.put(selectedInstance, selected.getValue() + 1);

      log.info(
          "{} for source {} routing from stale/unassigned {} to {}",
          logLabel,
          sourceId,
          assignedInstance,
          selectedInstance);
      return new OwnershipTarget(selectedInstance, true);
    }
  }
}
