// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConnectRoutingTargetResolverTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440701");
  private static final UUID SOURCE_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440702");
  private static final UUID SOURCE_THREE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440703");
  private static final String ASSIGNED_INSTANCE = "connect-assigned";
  private static final String REPLACEMENT_A = "connect-a";
  private static final String REPLACEMENT_B = "connect-b";

  private ConnectInstanceResolver instanceResolver;
  private ConnectRoutingTargetResolver targetResolver;

  @BeforeEach
  void setUp() {
    instanceResolver = mock(ConnectInstanceResolver.class);
    targetResolver = new ConnectRoutingTargetResolver(instanceResolver);
  }

  @Test
  void ownershipTarget_whenAssignedInstanceIsRoutable_keepsAssignment() {
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_A, 0), connectInfo(ASSIGNED_INSTANCE, 5)));

    var target = targetResolver.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "START");

    assertEquals(ASSIGNED_INSTANCE, target.instanceId());
    assertEquals(false, target.assignmentChanged());
  }

  @Test
  void ownershipTarget_whenAssignedInstanceIsStale_selectsLeastLoadedReplacement() {
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_A, 7), connectInfo(REPLACEMENT_B, 2)));

    var target = targetResolver.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "START");

    assertEquals(REPLACEMENT_B, target.instanceId());
    assertEquals(true, target.assignmentChanged());
  }

  @Test
  void ownershipTarget_forAddWithoutAssignment_selectsLeastLoadedRouteFreshOwner() {
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_A, 7), connectInfo(REPLACEMENT_B, 2)));

    var target = targetResolver.resolveOwnershipTarget(SOURCE_ID, null, "ADD");

    assertEquals(REPLACEMENT_B, target.instanceId());
    assertEquals(true, target.assignmentChanged());
  }

  @Test
  void ownershipTarget_withFetchedInstances_selectsLeastLoadedReplacement() {
    var target =
        targetResolver.resolveOwnershipTarget(
            SOURCE_ID,
            ASSIGNED_INSTANCE,
            "START",
            List.of(connectInfo(REPLACEMENT_A, 7), connectInfo(REPLACEMENT_B, 2)));

    assertEquals(REPLACEMENT_B, target.instanceId());
    assertEquals(true, target.assignmentChanged());
  }

  @Test
  void batchRoutableInstances_waitsForCurrentlyFreshWorkerCount() {
    var routableInstances = List.of(connectInfo(REPLACEMENT_A, 0), connectInfo(REPLACEMENT_B, 0));
    when(instanceResolver.getRoutableInstancesForCurrentRegistry(any()))
        .thenReturn(routableInstances);

    var result = targetResolver.batchRoutableInstances();

    assertEquals(routableInstances, result);
    verify(instanceResolver).getRoutableInstancesForCurrentRegistry(any());
  }

  @Test
  void batchOwnershipTargetSelector_whenEqualZeroLoads_spreadsStaleAndUnassignedSources() {
    var selector =
        targetResolver.batchOwnershipTargetSelector(
            List.of(connectInfo(REPLACEMENT_B, 0), connectInfo(REPLACEMENT_A, 0)),
            Arrays.asList(ASSIGNED_INSTANCE, null, "connect-old"));

    var first = selector.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "AUTOSTART");
    var second = selector.resolveOwnershipTarget(SOURCE_TWO_ID, null, "AUTOSTART");
    var third = selector.resolveOwnershipTarget(SOURCE_THREE_ID, "connect-old", "AUTOSTART");

    assertEquals(REPLACEMENT_A, first.instanceId());
    assertEquals(REPLACEMENT_B, second.instanceId());
    assertEquals(REPLACEMENT_A, third.instanceId());
  }

  @Test
  void batchOwnershipTargetSelector_seedsProjectedLoadFromRouteFreshDesiredAssignments() {
    var selector =
        targetResolver.batchOwnershipTargetSelector(
            List.of(connectInfo(REPLACEMENT_A, 0), connectInfo(REPLACEMENT_B, 0)),
            List.of(REPLACEMENT_A, REPLACEMENT_A));

    var target = selector.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "AUTOSTART");

    assertEquals(REPLACEMENT_B, target.instanceId());
    assertEquals(true, target.assignmentChanged());
  }

  @Test
  void batchOwnershipTargetSelector_preservesRouteFreshOwnerEvenWhenAnotherWorkerIsLowerLoad() {
    var selector =
        targetResolver.batchOwnershipTargetSelector(
            List.of(connectInfo(REPLACEMENT_A, 0), connectInfo(ASSIGNED_INSTANCE, 5)),
            List.of(ASSIGNED_INSTANCE));

    var target = selector.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "AUTOSTART");

    assertEquals(ASSIGNED_INSTANCE, target.instanceId());
    assertEquals(false, target.assignmentChanged());
  }

  @Test
  void ownershipTarget_whenNoRoutableInstances_throwsUnavailable() {
    when(instanceResolver.getRoutableInstances(any())).thenReturn(List.of());

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> targetResolver.resolveOwnershipTarget(SOURCE_ID, ASSIGNED_INSTANCE, "START"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
  }

  @Test
  void knownOwnerTarget_whenAssignedInstanceIsStale_doesNotPickReplacement() {
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_A, 0), connectInfo(REPLACEMENT_B, 1)));

    var target = targetResolver.resolveKnownOwnerTarget(SOURCE_ID, ASSIGNED_INSTANCE, "STOP");

    assertTrue(target.isEmpty());
  }

  private static ConnectInstanceInfo connectInfo(String instanceId, int connectionCount) {
    return new ConnectInstanceInfo(
        instanceId, Instant.now(), connectionCount, instanceId + "-reg", 1);
  }
}
