// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class SourceLayerRelationshipUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID LOCKED_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID ACTIVE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449003");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449004");
  private static final Caller ACTOR = TestActors.of(USER_ID);

  private ServerManager<Server> serverManager;
  private LayerManager<Layer, LayerMember> layerManager;
  private DataSourceRuntimeService runtimeService;
  private AuditRecorder auditRecorder;
  private SourceLayerRelationshipUsecase usecase;

  @BeforeEach
  void setUp() {
    serverManager = mock(ServerManager.class);
    layerManager = mock(LayerManager.class);
    runtimeService = mock(DataSourceRuntimeService.class);
    auditRecorder = mock(AuditRecorder.class);
    usecase =
        new SourceLayerRelationshipUsecase(
            serverManager,
            layerManager,
            runtimeService,
            mock(ConnectionMetadataGateway.class),
            auditRecorder,
            new DataSourceAuditProjection(),
            new LayerAuditProjection());
  }

  @Test
  void updateSourceLayers_whenTargetLayerIsLockedForMerge_returnsConflictWithoutMutation() {
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(LOCKED_LAYER_ID), Set.of());
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(lockedLayer()));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.updateSourceLayers(SOURCE_ID, request, ACTOR));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(serverManager, never()).updateLayers(SOURCE_ID, request);
  }

  @Test
  void deleteSourceLayers_whenTargetLayerIsLockedForMerge_returnsConflictWithoutMutation() {
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(lockedLayer()));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.deleteSourceLayers(SOURCE_ID, Set.of(LOCKED_LAYER_ID), ACTOR));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(serverManager, never()).deleteLayers(SOURCE_ID, Set.of(LOCKED_LAYER_ID));
  }

  @Test
  void updateSourceLayers_whenTargetLayerIsActive_updatesRelationship() {
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(ACTIVE_LAYER_ID), Set.of());
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(activeLayer()));
    when(layerManager.listLayerMembers(ACTIVE_LAYER_ID)).thenReturn(List.of());

    var result = usecase.updateSourceLayers(SOURCE_ID, request, ACTOR);

    assertEquals(1, result.notifications().size());
    verify(serverManager).updateLayers(SOURCE_ID, request);
  }

  @Test
  void deleteSourceLayers_whenSourceIsArchived_stillDeletesRelationship() {
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(new DataSourceRuntime(archivedDataSource(), null));
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(activeLayer()));
    when(layerManager.listLayerMembers(ACTIVE_LAYER_ID)).thenReturn(List.of());

    var result = usecase.deleteSourceLayers(SOURCE_ID, Set.of(ACTIVE_LAYER_ID), ACTOR);

    assertEquals(1, result.notifications().size());
    verify(serverManager).deleteLayers(SOURCE_ID, Set.of(ACTIVE_LAYER_ID));
  }

  @Test
  void updateSourceLayers_whenSourceIsArchived_returnsConflictWithoutMutation() {
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(ACTIVE_LAYER_ID), Set.of());
    when(runtimeService.getWithRuntime(SOURCE_ID, RecordVisibility.ALL))
        .thenReturn(new DataSourceRuntime(archivedDataSource(), null));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.updateSourceLayers(SOURCE_ID, request, ACTOR));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(serverManager, never()).updateLayers(SOURCE_ID, request);
  }

  /**
   * One act, two ledgers: the source's drawer says which layer it gained, the layer's drawer says
   * which source it gained, and both come from the same computed delta.
   */
  @Test
  void updateSourceLayers_recordsOneRowOnTheSourceAndOneOnEachLayer() {
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(ACTIVE_LAYER_ID), Set.of());
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(activeLayer()));
    when(layerManager.listLayerMembers(ACTIVE_LAYER_ID)).thenReturn(List.of());
    // Empty before the write, linked after: the row is built from what the link set became.
    when(serverManager.getLayers(SOURCE_ID))
        .thenReturn(Set.of(), Set.of(link(activeLayer(), ServerRole.ADMIN)));

    usecase.updateSourceLayers(SOURCE_ID, request, ACTOR);

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder, times(2)).record(recorded.capture());
    var sourceRow = recorded.getAllValues().getFirst();
    assertEquals(EntityType.DATA_SOURCE, sourceRow.entityType());
    assertEquals(SOURCE_ID, sourceRow.entityId());
    assertEquals(Operation.LAYERS_CHANGED, sourceRow.operation());
    assertEquals(ACTOR, sourceRow.actor());
    assertEquals(Set.of(ACTIVE_LAYER_ID), sourceRow.change().delta().added().keySet());
    assertEquals(ServerRole.ADMIN, sourceRow.change().delta().added().get(ACTIVE_LAYER_ID));

    var layerRow = recorded.getAllValues().get(1);
    assertEquals(EntityType.LAYER, layerRow.entityType());
    assertEquals(ACTIVE_LAYER_ID, layerRow.entityId());
    assertEquals(Operation.DATA_SOURCES_CHANGED, layerRow.operation());
    assertEquals(Set.of(SOURCE_ID), layerRow.change().delta().added().keySet());
    assertEquals(ServerRole.ADMIN, layerRow.change().delta().added().get(SOURCE_ID));
  }

  /** A re-saved dialog rewrites identical rows, which is not a change in either ledger. */
  @Test
  void updateSourceLayers_recordsNothingWhenTheLayerAlreadyHoldsTheRequestedRole() {
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(ACTIVE_LAYER_ID), Set.of());
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(activeLayer()));
    when(layerManager.listLayerMembers(ACTIVE_LAYER_ID)).thenReturn(List.of());
    when(serverManager.getLayers(SOURCE_ID))
        .thenReturn(Set.of(link(activeLayer(), ServerRole.ADMIN)));

    usecase.updateSourceLayers(SOURCE_ID, request, ACTOR);

    verify(serverManager).updateLayers(SOURCE_ID, request);
    verifyNoInteractions(auditRecorder);
  }

  /** Only ids that actually held a link are reported as unlinked. */
  @Test
  void deleteSourceLayers_recordsNothingForALayerThatWasNotLinked() {
    when(layerManager.list(any(LayerReadFilter.class))).thenReturn(List.of(activeLayer()));
    when(layerManager.listLayerMembers(ACTIVE_LAYER_ID)).thenReturn(List.of());
    when(serverManager.getLayers(SOURCE_ID)).thenReturn(Set.of());

    usecase.deleteSourceLayers(SOURCE_ID, Set.of(ACTIVE_LAYER_ID), ACTOR);

    verify(serverManager).deleteLayers(SOURCE_ID, Set.of(ACTIVE_LAYER_ID));
    verifyNoInteractions(auditRecorder);
  }

  private static SourceLayer link(Layer layer, ServerRole role) {
    return new SourceLayer.Builder().withLayer(layer).withRole(role).build();
  }

  private static DataSource archivedDataSource() {
    // DataSource is sealed and can't be mocked — build a real SocketDataSource instead.
    return new SocketDataSource(
        SOURCE_ID,
        "Archived Source",
        "tenant",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ_WRITE,
        true,
        null,
        Instant.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  private static Layer lockedLayer() {
    return new Layer.Builder()
        .withId(LOCKED_LAYER_ID)
        .withLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED)
        .build();
  }

  private static Layer activeLayer() {
    return new Layer.Builder()
        .withId(ACTIVE_LAYER_ID)
        .withLifecycleState(LayerLifecycleState.ACTIVE)
        .build();
  }
}
