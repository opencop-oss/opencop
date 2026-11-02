// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EventAttachmentPolicyTest {

  @Mock private EventAuthorizationPolicy eventAuth;
  @Mock private EventAttachmentRepository eventAttachmentRepository;
  private final AuthorizationService authz = new AuthorizationService();

  private EventAttachmentPolicy policy;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    policy = new EventAttachmentPolicy(eventAuth, eventAttachmentRepository, authz, 20);
  }

  @Test
  void assertCanCreate_authorizesAndChecksAttachmentCap() {
    var principal = principal();

    policy.assertCanCreate(principal, SOURCE_ID, OBJECT_ID);

    verify(eventAuth)
        .assertEditorForActiveResource(principal, SOURCE_ID, OBJECT_ID, "upload attachment");
    verify(eventAttachmentRepository).countAttachmentsByObjectId(SOURCE_ID, OBJECT_ID);
  }

  @Test
  void assertCanCreate_whenAuthorizationFailsPropagatesError() {
    var principal = principal();
    doThrow(new GlobalOpenCopException("forbidden", HttpStatus.FORBIDDEN))
        .when(eventAuth)
        .assertEditorForActiveResource(
            eq(principal), eq(SOURCE_ID), eq(OBJECT_ID), eq("upload attachment"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanCreate(principal, SOURCE_ID, OBJECT_ID));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertCanCreate_whenAttachmentCapReachedThrows400() {
    when(eventAttachmentRepository.countAttachmentsByObjectId(SOURCE_ID, OBJECT_ID)).thenReturn(20);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanCreate(principal(), SOURCE_ID, OBJECT_ID));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void assertCanDelete_authorizesAndChecksAttachmentAssociation() {
    var principal = principal();
    var attachmentId = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, attachmentId)).thenReturn(true);

    policy.assertCanDelete(principal, SOURCE_ID, OBJECT_ID, attachmentId);

    verify(eventAuth)
        .assertEditorForActiveResource(principal, SOURCE_ID, OBJECT_ID, "delete attachment");
    verify(eventAttachmentRepository).exists(SOURCE_ID, OBJECT_ID, attachmentId);
  }

  @Test
  void assertCanDelete_whenAssociationMissingThrows404() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanDelete(principal(), SOURCE_ID, OBJECT_ID, UUID.randomUUID()));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertCanList_authorizesEventMembership() {
    var principal = principal();

    policy.assertCanList(principal, SOURCE_ID, OBJECT_ID);

    verify(eventAuth).assertMember(principal, SOURCE_ID, OBJECT_ID);
  }

  @Test
  void assertCanGet_authorizesEventMembershipAndChecksAttachmentAssociation() {
    var principal = principal();
    var attachmentId = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, attachmentId)).thenReturn(true);

    policy.assertCanGet(principal, SOURCE_ID, OBJECT_ID, attachmentId);

    verify(eventAuth).assertMember(principal, SOURCE_ID, OBJECT_ID);
    verify(eventAttachmentRepository).exists(SOURCE_ID, OBJECT_ID, attachmentId);
  }

  @Test
  void assertCanGet_whenAssociationMissingThrows404() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanGet(principal(), SOURCE_ID, OBJECT_ID, UUID.randomUUID()));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void canGet_whenMemberAndAssociationExistsReturnsTrue() {
    var principal = principal();
    var attachmentId = UUID.randomUUID();
    when(eventAuth.canMember(principal, SOURCE_ID, OBJECT_ID)).thenReturn(true);
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, attachmentId)).thenReturn(true);

    assertTrue(policy.canGet(principal, SOURCE_ID, OBJECT_ID, attachmentId));
  }

  @Test
  void canGet_whenMemberDeniedReturnsFalseWithoutCheckingAssociation() {
    var principal = principal();
    var attachmentId = UUID.randomUUID();
    when(eventAuth.canMember(principal, SOURCE_ID, OBJECT_ID)).thenReturn(false);

    assertFalse(policy.canGet(principal, SOURCE_ID, OBJECT_ID, attachmentId));

    verify(eventAttachmentRepository, never()).exists(SOURCE_ID, OBJECT_ID, attachmentId);
  }

  @Test
  void canGet_whenMemberCheckThrowsReturnsFalse() {
    var principal = principal();
    var attachmentId = UUID.randomUUID();
    doThrow(new GlobalOpenCopException("not found", HttpStatus.NOT_FOUND))
        .when(eventAuth)
        .canMember(principal, SOURCE_ID, OBJECT_ID);

    assertFalse(policy.canGet(principal, SOURCE_ID, OBJECT_ID, attachmentId));
  }

  @Test
  void assertOwnerOrAdmin_whenOwnerPasses() {
    policy.assertOwnerOrAdmin(principal(), USER_ID);
  }

  @Test
  void assertOwnerOrAdmin_whenAdminPasses() {
    policy.assertOwnerOrAdmin(principal(false, true), UUID.randomUUID());
  }

  @Test
  void assertOwnerOrAdmin_whenLoginAdminIsCurrentlyDemotedThrows403() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertOwnerOrAdmin(principal(true, false), UUID.randomUUID()));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertOwnerOrAdmin_whenNeitherThrows403() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertOwnerOrAdmin(principal(), UUID.randomUUID()));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertOwnerOrAdmin_whenOwnerIsNullNonAdminThrows403() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertOwnerOrAdmin(principal(), null));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertOwnerOrAdmin_whenOwnerIsNullAdminPasses() {
    policy.assertOwnerOrAdmin(principal(false, true), null);
  }

  private static OpenCopPrincipal principal() {
    return principal(false, false);
  }

  private static OpenCopPrincipal principal(boolean loginAdmin, boolean currentAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, loginAdmin)
        .withRequestCurrentGlobalAdministrator(currentAdmin);
  }
}
