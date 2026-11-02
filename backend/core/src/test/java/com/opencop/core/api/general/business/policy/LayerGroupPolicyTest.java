// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerGroupPolicyTest {

  private static final UUID U1 = UUID.fromString("550e8400-e29b-41d4-a716-000000000001");
  private static final UUID U2 = UUID.fromString("550e8400-e29b-41d4-a716-000000000002");
  private static final UUID U3 = UUID.fromString("550e8400-e29b-41d4-a716-000000000003");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-000000000010");

  // ── assertRetainsOwner ────────────────────────────────────────────────────

  @Test
  void assertRetainsOwner_singleOwnerPresent_doesNotThrow() {
    assertDoesNotThrow(() -> LayerGroupPolicy.assertRetainsOwner(Map.of(U1, LayerGroupRole.OWNER)));
  }

  @Test
  void assertRetainsOwner_noOwners_throwsPersistenceException() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> LayerGroupPolicy.assertRetainsOwner(Map.of(U1, LayerGroupRole.ADMIN)));

    assertTrue(ex.getMessage().contains("OWNER"));
  }

  @Test
  void assertRetainsOwner_multipleOwners_doesNotThrow() {
    assertDoesNotThrow(
        () ->
            LayerGroupPolicy.assertRetainsOwner(
                Map.of(U1, LayerGroupRole.OWNER, U2, LayerGroupRole.OWNER)));
  }

  @Test
  void assertRetainsOwner_singleAdmin_throwsPersistenceException() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> LayerGroupPolicy.assertRetainsOwner(Map.of(U1, LayerGroupRole.ADMIN)));

    assertTrue(ex.getMessage().contains("OWNER"));
  }

  @Test
  void assertRetainsOwner_emptyMap_doesNotThrow() {
    assertDoesNotThrow(() -> LayerGroupPolicy.assertRetainsOwner(Map.of()));
  }

  // ── affectsOwnerList ──────────────────────────────────────────────────────

  @Test
  void affectsOwnerList_noOwnerChange_returnsFalse() {
    var current = Map.of(U1, LayerGroupRole.ADMIN, U2, LayerGroupRole.OWNER);
    var projected = Map.of(U1, LayerGroupRole.MAINTAINER, U2, LayerGroupRole.OWNER);

    assertTrue(!LayerGroupPolicy.affectsOwnerList(current, projected));
  }

  @Test
  void affectsOwnerList_ownerDemoted_returnsTrue() {
    var current = Map.of(U1, LayerGroupRole.OWNER, U2, LayerGroupRole.ADMIN);
    var projected = Map.of(U1, LayerGroupRole.ADMIN, U2, LayerGroupRole.ADMIN);

    assertTrue(LayerGroupPolicy.affectsOwnerList(current, projected));
  }

  @Test
  void affectsOwnerList_newOwnerAdded_returnsTrue() {
    var current = Map.of(U1, LayerGroupRole.OWNER);
    var projected = Map.of(U1, LayerGroupRole.OWNER, U2, LayerGroupRole.OWNER);

    assertTrue(LayerGroupPolicy.affectsOwnerList(current, projected));
  }

  @Test
  void affectsOwnerList_ownerRemoved_returnsTrue() {
    var current = Map.of(U1, LayerGroupRole.OWNER, U2, LayerGroupRole.MEMBER);
    var projected = Map.of(U2, LayerGroupRole.MEMBER);

    assertTrue(LayerGroupPolicy.affectsOwnerList(current, projected));
  }

  // ── assertRetainsOwner two-map overload ───────────────────────────────────

  @Test
  void assertRetainsOwner_twoMap_userOwnerOnly_doesNotThrow() {
    assertDoesNotThrow(
        () ->
            LayerGroupPolicy.assertRetainsOwner(
                Map.of(U1, LayerGroupRole.OWNER), Map.of(U2, LayerGroupRole.MAINTAINER)));
  }

  @Test
  void assertRetainsOwner_twoMap_userGroupOwnerOnly_doesNotThrow() {
    assertDoesNotThrow(
        () ->
            LayerGroupPolicy.assertRetainsOwner(
                Map.of(U1, LayerGroupRole.ADMIN), Map.of(U2, LayerGroupRole.OWNER)));
  }

  @Test
  void assertRetainsOwner_twoMap_noOwnerInEitherMap_throwsPersistenceException() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                LayerGroupPolicy.assertRetainsOwner(
                    Map.of(U1, LayerGroupRole.ADMIN), Map.of(U2, LayerGroupRole.MAINTAINER)));

    assertTrue(ex.getMessage().contains("OWNER"));
  }

  @Test
  void assertRetainsOwner_twoMap_bothMapsEmpty_throwsPersistenceException() {
    assertThrows(
        PersistenceException.class, () -> LayerGroupPolicy.assertRetainsOwner(Map.of(), Map.of()));
  }

  // ── affectsOwnerList four-map overload ────────────────────────────────────

  @Test
  void affectsOwnerList_fourMap_ugOwnerAdded_returnsTrue() {
    var currentUsers = Map.of(U1, LayerGroupRole.OWNER);
    var projectedUsers = Map.of(U1, LayerGroupRole.OWNER);
    var currentUGs = Map.of(U2, LayerGroupRole.MAINTAINER);
    var projectedUGs = Map.of(U2, LayerGroupRole.OWNER);

    assertTrue(
        LayerGroupPolicy.affectsOwnerList(currentUsers, projectedUsers, currentUGs, projectedUGs));
  }

  @Test
  void affectsOwnerList_fourMap_noOwnerChange_returnsFalse() {
    var currentUsers = Map.of(U1, LayerGroupRole.OWNER);
    var projectedUsers = Map.of(U1, LayerGroupRole.OWNER);
    var currentUGs = Map.of(U2, LayerGroupRole.MAINTAINER);
    var projectedUGs = Map.of(U2, LayerGroupRole.MAINTAINER);

    assertTrue(
        !LayerGroupPolicy.affectsOwnerList(currentUsers, projectedUsers, currentUGs, projectedUGs));
  }

  @Test
  void memberPatchAffectsOwnerList_usesCurrentUserGroupsAsUnchangedSide() {
    var currentUsers = Map.of(U1, LayerGroupRole.OWNER);
    var projectedUsers = Map.of(U1, LayerGroupRole.MEMBER);
    var currentUGs = Map.of(U2, LayerGroupRole.MEMBER);

    assertEquals(
        LayerGroupPolicy.affectsOwnerList(currentUsers, projectedUsers, currentUGs, currentUGs),
        LayerGroupPolicy.memberPatchAffectsOwnerList(currentUsers, projectedUsers, currentUGs));
  }

  @Test
  void userGroupPatchAffectsOwnerList_usesCurrentUsersAsUnchangedSide() {
    var currentUsers = Map.of(U1, LayerGroupRole.MEMBER);
    var currentUGs = Map.of(U2, LayerGroupRole.MEMBER);
    var projectedUGs = Map.of(U2, LayerGroupRole.OWNER);

    assertEquals(
        LayerGroupPolicy.affectsOwnerList(currentUsers, currentUsers, currentUGs, projectedUGs),
        LayerGroupPolicy.userGroupPatchAffectsOwnerList(currentUsers, currentUGs, projectedUGs));
  }

  @Test
  void assertRetainsOwner_singleMapDelegation_stillEnforcesOwner() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> LayerGroupPolicy.assertRetainsOwner(Map.of(U1, LayerGroupRole.ADMIN)));

    assertTrue(ex.getMessage().contains("OWNER"));
  }

  @Test
  void assertContainerRole_missingRole_throwsReasonCodedException() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                LayerGroupPolicy.assertContainerRole(
                    Optional.empty(), false, LayerGroupRole.ADMIN, LayerGroupRole.OWNER));

    assertEquals(AuthzReasonCode.MISSING_LAYER_GROUP_ROLE, ex.reasonCode());
  }

  @Test
  void assertContainerRole_insufficientRole_throwsReasonCodedException() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                LayerGroupPolicy.assertContainerRole(
                    Optional.of(LayerGroupRole.MEMBER),
                    false,
                    LayerGroupRole.ADMIN,
                    LayerGroupRole.OWNER));

    assertEquals(AuthzReasonCode.INSUFFICIENT_LAYER_GROUP_ROLE, ex.reasonCode());
  }

  // ── assertCanAssignLayer ───────────────────────────────────────────────────

  @Test
  void assertCanAssignLayer_owner_doesNotThrow() {
    assertDoesNotThrow(
        () -> LayerGroupPolicy.assertCanAssignLayer(Optional.of(LayerRole.OWNER), LAYER_ID));
  }

  @Test
  void assertCanAssignLayer_admin_doesNotThrow() {
    assertDoesNotThrow(
        () -> LayerGroupPolicy.assertCanAssignLayer(Optional.of(LayerRole.ADMIN), LAYER_ID));
  }

  /** EDITOR is the floor, so a MAINTAINER curating layers they can edit must keep working. */
  @Test
  void assertCanAssignLayer_editor_doesNotThrow() {
    assertDoesNotThrow(
        () -> LayerGroupPolicy.assertCanAssignLayer(Optional.of(LayerRole.EDITOR), LAYER_ID));
  }

  @Test
  void assertCanAssignLayer_member_throwsReasonCodedException() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> LayerGroupPolicy.assertCanAssignLayer(Optional.of(LayerRole.MEMBER), LAYER_ID));

    assertEquals(AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE, ex.reasonCode());
    assertTrue(ex.getMessage().contains("requires EDITOR or above on the layer"));
  }

  @Test
  void assertCanAssignLayer_noRole_throwsReasonCodedException() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> LayerGroupPolicy.assertCanAssignLayer(Optional.empty(), LAYER_ID));

    assertEquals(AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE, ex.reasonCode());
  }

  // ── assertLayerIsPrivate ─────────────────────────────────────────────────

  @Test
  void assertLayerIsPrivate_privateLayer_doesNotThrow() {
    assertDoesNotThrow(
        () -> LayerGroupPolicy.assertLayerIsPrivate(LayerAccessScope.PRIVATE, LAYER_ID));
  }

  /**
   * Closes the redistribution hole: a GLOBAL layer resolves to MEMBER for everyone via the
   * access-scope fallback, so a non-admin must not be able to assign one.
   *
   * <p>The reason code must stay distinct from the role-based gates. This denial turns on the
   * layer's scope, not on the actor's role, and both gates run back to back in {@code
   * LayerGroupLayersUseCase} — a shared code would make the audit trail unable to say which one
   * rejected.
   */
  @Test
  void assertLayerIsPrivate_globalLayer_throwsReasonCodedException() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> LayerGroupPolicy.assertLayerIsPrivate(LayerAccessScope.GLOBAL, LAYER_ID));

    assertEquals(
        AuthzReasonCode.INSUFFICIENT_LAYER_SCOPE_FOR_LAYER_GROUP_ASSIGNMENT, ex.reasonCode());
    assertTrue(ex.getMessage().contains("only admins can assign a GLOBAL layer"));
  }
}
