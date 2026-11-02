// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceAssignmentReconcilerTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440801");
  private static final UUID SOURCE_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440803");
  private static final UUID SOURCE_THREE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440804");
  private static final String STALE_INSTANCE_ID = "connect-old";
  private static final String ASSIGNED_INSTANCE_ID = "connect-assigned";
  private static final String REPLACEMENT_INSTANCE_ID = "connect-replacement";
  private static final String BUSY_INSTANCE_ID = "connect-busy";
  private static final String REPLACEMENT_A = "connect-a";
  private static final String REPLACEMENT_B = "connect-b";
  private static final DataSourceReadFilter ENABLED_ACTIVE_FILTER =
      new DataSourceReadFilter(null, null, RecordVisibility.ACTIVE, null, true, null);

  private DataSourceRepository dataSourceRepository;
  private ConnectInstanceResolver instanceResolver;
  private SocketReconciliationNudgePublisher nudgePublisher;
  private DataSourceAssignmentReconciler reconciler;

  @BeforeEach
  void setUp() {
    dataSourceRepository = mock(DataSourceRepository.class);
    instanceResolver = mock(ConnectInstanceResolver.class);
    nudgePublisher = mock(SocketReconciliationNudgePublisher.class);
    reconciler =
        new DataSourceAssignmentReconciler(
            dataSourceRepository,
            new ConnectRoutingTargetResolver(instanceResolver),
            nudgePublisher);
  }

  @Test
  void reconcile_whenAssignedInstanceIsStale_reassignsAndPublishesStartAfterPersist()
      throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(true, STALE_INSTANCE_ID)));
    whenBatchRoutableInstances(
        List.of(connectInfo(BUSY_INSTANCE_ID, 9), connectInfo(REPLACEMENT_INSTANCE_ID, 1)));

    var result = reconciler.reconcileOnce();

    assertEquals(1, result.enabledSocketCount());
    assertEquals(1, result.reassignedCount());

    var order = inOrder(dataSourceRepository, nudgePublisher);
    order
        .verify(dataSourceRepository)
        .setAssignedInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID, null);
    order.verify(nudgePublisher).requestReconciliation();
  }

  @Test
  void reconcile_whenSourceIsUnassigned_assignsLeastLoadedRoutableInstance() throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(true, null)));
    whenBatchRoutableInstances(
        List.of(connectInfo(BUSY_INSTANCE_ID, 7), connectInfo(REPLACEMENT_INSTANCE_ID, 2)));

    var result = reconciler.reconcileOnce();

    assertEquals(1, result.reassignedCount());
    verify(dataSourceRepository).setAssignedInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID, null);
    verify(nudgePublisher).requestReconciliation();
  }

  @Test
  void reconcile_whenEqualLoadWorkers_spreadsStaleAndUnassignedSourcesAcrossBatch()
      throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(
            List.of(
                socketSource(SOURCE_ID, "Alpha", true, STALE_INSTANCE_ID),
                socketSource(SOURCE_TWO_ID, "Bravo", true, null),
                socketSource(SOURCE_THREE_ID, "Charlie", true, STALE_INSTANCE_ID)));
    whenBatchRoutableInstances(
        List.of(connectInfo(REPLACEMENT_B, 0), connectInfo(REPLACEMENT_A, 0)));

    var result = reconciler.reconcileOnce();

    assertEquals(3, result.enabledSocketCount());
    assertEquals(0, result.unchangedCount());
    assertEquals(3, result.reassignedCount());
    verify(dataSourceRepository).setAssignedInstance(SOURCE_ID, REPLACEMENT_A, null);
    verify(dataSourceRepository).setAssignedInstance(SOURCE_TWO_ID, REPLACEMENT_B, null);
    verify(dataSourceRepository).setAssignedInstance(SOURCE_THREE_ID, REPLACEMENT_A, null);
    verify(nudgePublisher, times(1)).requestReconciliation();
  }

  @Test
  void reconcile_whenAssignedInstanceIsRoutable_leavesSourceAlone() throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(true, ASSIGNED_INSTANCE_ID)));
    whenBatchRoutableInstances(
        List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0), connectInfo(ASSIGNED_INSTANCE_ID, 5)));

    var result = reconciler.reconcileOnce();

    assertEquals(1, result.enabledSocketCount());
    assertEquals(1, result.unchangedCount());
    assertEquals(0, result.reassignedCount());
    verify(instanceResolver).getRoutableInstancesForCurrentRegistry(any());
    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void reconcile_whenDisabledSourceIsReturnedDefensively_leavesSourceAlone() throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(false, STALE_INSTANCE_ID)));

    var result = reconciler.reconcileOnce();

    assertEquals(0, result.enabledSocketCount());
    verify(instanceResolver, never()).getAllInstanceIds();
    verify(instanceResolver, never()).getRoutableInstancesForCurrentRegistry(any());
    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void reconcile_whenNoRoutableInstances_leavesAssignmentUnchanged() throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(true, STALE_INSTANCE_ID)));
    whenBatchRoutableInstances(List.of());

    var result = reconciler.reconcileOnce();

    assertEquals(1, result.enabledSocketCount());
    assertEquals(0, result.reassignedCount());
    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void reconcile_whenReconciliationNudgeFails_keepsPersistedAssignment() throws Exception {
    when(dataSourceRepository.findAll(ENABLED_ACTIVE_FILTER))
        .thenReturn(List.of(socketSource(true, STALE_INSTANCE_ID)));
    whenBatchRoutableInstances(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    doThrow(new IllegalStateException("boom")).when(nudgePublisher).requestReconciliation();

    var result = reconciler.reconcileOnce();

    assertEquals(1, result.reassignedCount());
    verify(dataSourceRepository).setAssignedInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID, null);
    verify(nudgePublisher).requestReconciliation();
  }

  private static ConnectInstanceInfo connectInfo(String instanceId, int connectionCount) {
    return new ConnectInstanceInfo(
        instanceId, Instant.now(), connectionCount, instanceId + "-reg", 1);
  }

  private void whenBatchRoutableInstances(List<ConnectInstanceInfo> instances) {
    when(instanceResolver.getRoutableInstancesForCurrentRegistry(any())).thenReturn(instances);
  }

  private static SocketDataSource socketSource(boolean enabled, String assignedInstance) {
    return socketSource(SOURCE_ID, "Alpha", enabled, assignedInstance);
  }

  private static SocketDataSource socketSource(
      UUID sourceId, String name, boolean enabled, String assignedInstance) {
    var spec =
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity()));
    return new SocketDataSource(
        sourceId,
        name,
        "default",
        spec,
        null,
        null,
        null,
        AccessMode.READ,
        enabled,
        assignedInstance,
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        true);
  }
}
