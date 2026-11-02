// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.usecase.event.HistoricalErrorCode;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HistoricalTracksAuthorizationPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");

  private AccessRepository<ServerRole> sourceAccess;
  private AccessRepository<LayerRole> layerAccess;
  private HistoricalTracksAuthorizationPolicy policy;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    sourceAccess = mock(AccessRepository.class);
    layerAccess = mock(AccessRepository.class);
    policy = new HistoricalTracksAuthorizationPolicy(sourceAccess, layerAccess);
  }

  @Test
  void authorizeSubjects_whenGlobalAdmin_returnsSubjectsWithoutAccessChecks() {
    var subjects = List.of(sourceSubject());

    var authorized = policy.authorizeSubjects(principal(true), subjects);

    assertEquals(subjects, authorized);
    verify(sourceAccess, never()).userRole(USER_ID, SOURCE_ID);
    verify(layerAccess, never()).userRole(USER_ID, LAYER_ID);
  }

  @Test
  void authorizeSubjects_whenSourceAccessExists_returnsSubjectsInRequestOrder() {
    var subjects = List.of(sourceSubject());
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);

    var authorized = policy.authorizeSubjects(principal(false), subjects);

    assertEquals(subjects, authorized);
  }

  @Test
  void authorizeSubjects_whenLayerAccessExists_returnsSubjectsInRequestOrder() {
    var subjects = List.of(layerSubject());
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.MEMBER);

    var authorized = policy.authorizeSubjects(principal(false), subjects);

    assertEquals(subjects, authorized);
  }

  @Test
  void authorizeSubjects_whenMixedSourceAndLayerSubjects_returnsSubjectsInOriginalOrder() {
    var sourceFirst = sourceSubject();
    var layerMiddle = layerSubject();
    var sourceSecond = sourceSubject(UUID.fromString("550e8400-e29b-41d4-a716-446655440124"));
    var subjects = List.of(sourceFirst, layerMiddle, sourceSecond);
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);
    when(sourceAccess.userRole(USER_ID, sourceSecond.subjectId())).thenReturn(ServerRole.VIEWER);
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.MEMBER);

    var authorized = policy.authorizeSubjects(principal(false), subjects);

    assertEquals(subjects, authorized);
  }

  @Test
  void authorizeSubjects_whenSourceAccessIsMissing_throwsForbidden() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.authorizeSubjects(principal(false), List.of(sourceSubject())));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertEquals(HistoricalErrorCode.HISTORICAL_SUBJECT_FORBIDDEN.name(), ex.getCode());
  }

  @Test
  void authorizeSubjects_whenLayerAccessIsMissing_throwsForbidden() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.authorizeSubjects(principal(false), List.of(layerSubject())));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertEquals(HistoricalErrorCode.HISTORICAL_SUBJECT_FORBIDDEN.name(), ex.getCode());
  }

  private static HistoricalSubjectRequest sourceSubject() {
    return new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID);
  }

  private static HistoricalSubjectRequest layerSubject() {
    return new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID);
  }

  private static HistoricalSubjectRequest sourceSubject(UUID sourceId) {
    return new HistoricalSubjectRequest(SubjectType.SOURCE, sourceId);
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
        USER_ID, globalAdmin);
  }
}
