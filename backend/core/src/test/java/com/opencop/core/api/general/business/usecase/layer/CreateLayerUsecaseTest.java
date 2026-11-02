// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerCreateRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Creating a layer is one act with one row.
 *
 * <p>The creation, the creator's ownership and any initial members were three transactions before
 * they were collapsed into one, so the row has to describe the whole layer or none of it.
 */
class CreateLayerUsecaseTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID CREATOR = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID INVITEE = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");

  @SuppressWarnings("unchecked")
  private final LayerManager<Layer, LayerMember> layerManager = mock(LayerManager.class);

  private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
  private final OpenCopPrincipal caller = mock(OpenCopPrincipal.class);

  private CreateLayerUsecase usecase;

  @BeforeEach
  void setUp() {
    usecase = new CreateLayerUsecase(layerManager, auditRecorder, new LayerAuditProjection());
    when(caller.internalUserId()).thenReturn(CREATOR);
    when(layerManager.create(any(LayerCreateRequest.class), eq(CREATOR))).thenReturn(layer());
    when(layerManager.addLayerMembers(eq(LAYER_ID), any()))
        .thenReturn(Map.of(CREATOR, LayerRole.OWNER));
  }

  @Test
  void create_recordsTheLayerAndWhoItStartedWith() {
    var change = recordedChange(usecase.create("Recon North", null, null, caller));

    assertThat(change.changed())
        .containsExactly(
            new AuditChange.FieldChange("name", null, "Recon North"),
            new AuditChange.FieldChange("accessScope", null, LayerAccessScope.PRIVATE.name()),
            new AuditChange.FieldChange("defaultVisible", null, true));
    assertThat(change.delta().added()).containsOnlyKeys(CREATOR);
  }

  /** A creator who also appears in the initial list keeps OWNER, and the row says so. */
  @Test
  void create_recordsTheRolesThatWereApplied() {
    when(layerManager.addLayerMembers(eq(LAYER_ID), eq(Map.of(INVITEE, LayerRole.MEMBER))))
        .thenReturn(Map.of(INVITEE, LayerRole.MEMBER));

    var change =
        recordedChange(
            usecase.create("Recon North", null, Map.of(INVITEE, LayerRole.MEMBER), caller));

    assertThat(change.delta().added()).containsOnlyKeys(CREATOR, INVITEE);
    assertThat(change.delta().added().get(CREATOR)).isEqualTo(LayerRole.OWNER);
    assertThat(change.delta().added().get(INVITEE)).isEqualTo(LayerRole.MEMBER);
  }

  @Test
  void create_defaultsToAPrivateScope() {
    usecase.create("Recon North", null, null, caller);

    var request = ArgumentCaptor.forClass(LayerCreateRequest.class);
    verify(layerManager).create(request.capture(), eq(CREATOR));
    assertThat(request.getValue().accessScope()).isEqualTo(LayerAccessScope.PRIVATE);
  }

  /** A refused creation writes nothing, because nothing happened. */
  @Test
  void create_whenANonAdminAsksForAGlobalLayer_recordsNothing() {
    when(caller.isGlobalAdmin()).thenReturn(false);

    assertThatThrownBy(() -> usecase.create("Everyone", LayerAccessScope.GLOBAL, null, caller))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageContaining("admin");

    verify(layerManager, never()).create(any(), any());
    verify(auditRecorder, never()).record(any());
  }

  /** The row carries the scope the layer was stored with, not the one the request asked for. */
  @Test
  void create_whenAnAdminAsksForAGlobalLayer_recordsTheStoredScope() {
    when(caller.isGlobalAdmin()).thenReturn(true);
    when(layerManager.create(any(LayerCreateRequest.class), eq(CREATOR)))
        .thenReturn(
            new Layer.Builder()
                .withId(LAYER_ID)
                .withName("Everyone")
                .withAccessScope(LayerAccessScope.GLOBAL)
                .build());

    var change = recordedChange(usecase.create("Everyone", LayerAccessScope.GLOBAL, null, caller));

    assertThat(change.changed())
        .contains(new AuditChange.FieldChange("accessScope", null, LayerAccessScope.GLOBAL.name()));
  }

  private AuditChange recordedChange(Layer created) {
    assertThat(created.getId()).isEqualTo(LAYER_ID);
    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(recorded.capture());
    var event = recorded.getValue();
    assertThat(event.entityType()).isEqualTo(EntityType.LAYER);
    assertThat(event.entityId()).isEqualTo(LAYER_ID);
    assertThat(event.operation()).isEqualTo(Operation.CREATE);
    assertThat(event.actor().userId()).isEqualTo(CREATOR);
    return event.change();
  }

  private static Layer layer() {
    return new Layer.Builder()
        .withId(LAYER_ID)
        .withName("Recon North")
        .withAccessScope(LayerAccessScope.PRIVATE)
        .build();
  }
}
