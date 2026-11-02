// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** History rows describe what the write changed, not what the request asked for. */
class LayerMembersUsecaseTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID MEMBER = UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID STRANGER = UUID.fromString("550e8400-e29b-41d4-a716-446655447003");

  private LayerManager<Layer, LayerMember> layerManager;
  private AuditRecorder recorder;
  private LayerMembersUsecase useCase;
  private final OpenCopPrincipal caller = mock(OpenCopPrincipal.class);

  // A caller always has an id; the Caller type rejects one without. Stubbed here rather than left
  // null so these tests exercise the same construction production does.
  {
    when(caller.internalUserId()).thenReturn(UUID.randomUUID());
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    layerManager = mock(LayerManager.class);
    recorder = mock(AuditRecorder.class);
    useCase =
        new LayerMembersUsecase(
            layerManager,
            mock(LayerAuthorizationService.class),
            recorder,
            new LayerAuditProjection());
    when(layerManager.get(LAYER_ID))
        .thenReturn(new Layer.Builder().withId(LAYER_ID).withName("L").build());
  }

  /**
   * Drives the mocked manager through the two states the write moves between, because the row is
   * built from what the membership actually became. A mock that answers the same list before and
   * after records nothing, which is what used to make these assertions vacuous.
   */
  private void membershipMovesFrom(List<LayerMember> before, List<LayerMember> after) {
    when(layerManager.listLayerMembers(LAYER_ID)).thenReturn(before, after);
  }

  private static final List<LayerMember> ONE_MEMBER = List.of(member(MEMBER, LayerRole.MEMBER));

  @Test
  void removeMembers_recordsOnlyThoseWhoWereMembers() {
    membershipMovesFrom(ONE_MEMBER, List.of());

    useCase.removeMembers(LAYER_ID, Set.of(MEMBER, STRANGER), caller);

    var delta = recordedEvent().change().delta();
    assertThat(delta.removed()).containsExactly(MEMBER);
    assertThat(delta.added()).isEmpty();
  }

  @Test
  void removeMembers_whenNoneWasAMember_recordsNoRow() {
    membershipMovesFrom(ONE_MEMBER, ONE_MEMBER);

    useCase.removeMembers(LAYER_ID, Set.of(STRANGER), caller);

    verify(recorder, never()).record(any());
  }

  @Test
  void updateMemberRole_whenTheRoleIsUnchanged_recordsNoRow() {
    membershipMovesFrom(ONE_MEMBER, ONE_MEMBER);

    useCase.updateMemberRole(
        LAYER_ID, new UpdateLayerRoleRequest(Set.of(MEMBER), null, null, null), caller);

    verify(recorder, never()).record(any());
  }

  @Test
  void updateMemberRole_recordsARealPromotion() {
    membershipMovesFrom(ONE_MEMBER, List.of(member(MEMBER, LayerRole.EDITOR)));

    useCase.updateMemberRole(
        LAYER_ID, new UpdateLayerRoleRequest(null, null, Set.of(MEMBER), null), caller);

    var event = recordedEvent();
    assertThat(event.operation()).isEqualTo(Operation.MEMBERS_CHANGED);
    assertThat(event.change().delta().roleChanged()).containsOnlyKeys(MEMBER);
  }

  /**
   * The writer upserts, so a non-member named in any bucket is inserted and gains access. Reading
   * the membership after the write is what catches that; diffing against the request did not, and
   * an owner could be granted with no row at all.
   */
  @Test
  void updateMemberRole_recordsANonMemberTheUpsertInserted() {
    membershipMovesFrom(
        ONE_MEMBER, List.of(member(MEMBER, LayerRole.MEMBER), member(STRANGER, LayerRole.OWNER)));

    useCase.updateMemberRole(
        LAYER_ID, new UpdateLayerRoleRequest(Set.of(STRANGER), null, null, null), caller);

    var event = recordedEvent();
    assertThat(event.change().delta().added()).containsOnlyKeys(STRANGER);
    assertThat(event.change().delta().added().get(STRANGER)).isEqualTo(LayerRole.OWNER);
  }

  /**
   * An id sent in two buckets ends up with whichever role the writer applied last. The row follows
   * the writer because it reads the result, not the request.
   */
  @Test
  void updateMemberRole_recordsTheRoleTheWriterApplied() {
    membershipMovesFrom(ONE_MEMBER, List.of(member(MEMBER, LayerRole.OWNER)));

    useCase.updateMemberRole(
        LAYER_ID, new UpdateLayerRoleRequest(Set.of(MEMBER), null, Set.of(MEMBER), null), caller);

    var roleChanged = recordedEvent().change().delta().roleChanged();
    assertThat(roleChanged).containsOnlyKeys(MEMBER);
    assertThat(roleChanged.get(MEMBER)).isEqualTo(LayerRole.OWNER);
  }

  @Test
  void addMembers_whenEveryoneAlreadyHeldTheRole_recordsNoRow() {
    membershipMovesFrom(ONE_MEMBER, ONE_MEMBER);

    useCase.addMembers(LAYER_ID, java.util.Map.of(MEMBER, LayerRole.MEMBER), caller);

    verify(recorder, never()).record(any());
  }

  @Test
  void addMembers_locksTheLayerBeforeReadingTheMembership() {
    membershipMovesFrom(ONE_MEMBER, ONE_MEMBER);

    useCase.addMembers(LAYER_ID, java.util.Map.of(MEMBER, LayerRole.MEMBER), caller);

    var order = org.mockito.Mockito.inOrder(layerManager);
    order.verify(layerManager).lock(LAYER_ID);
    order.verify(layerManager).listLayerMembers(LAYER_ID);
  }

  private AuditEvent recordedEvent() {
    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    return captor.getValue();
  }

  private static LayerMember member(UUID userId, LayerRole role) {
    var member = new LayerMember();
    member.setUserId(userId);
    member.setRole(role);
    return member;
  }
}
