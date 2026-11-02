// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RetrieveEventHistoryUsecaseTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final String OBJECT_ID = "event-123";
  private static final Instant FROM = Instant.parse("2026-03-12T10:00:00Z");
  private static final Instant TO = Instant.parse("2026-03-12T11:00:00Z");
  private static final UUID USER_ID = UUID.randomUUID();

  private PostGisService service;
  private EventAuthorizationPolicy authorizationHelper;
  private RetrieveEventHistoryUsecase usecase;

  @BeforeEach
  void setUp() {
    service = mock(PostGisService.class);
    authorizationHelper = mock(EventAuthorizationPolicy.class);
    usecase = new RetrieveEventHistoryUsecase(service, authorizationHelper);
  }

  @Test
  void getHistory_bucketedDelegatesToService() throws PersistenceException {
    var principal = principal();
    var response = mock(EventHistoryResponse.class);
    when(service.getEventHistoryBucketed(SOURCE_ID, OBJECT_ID, FROM, TO, 1000))
        .thenReturn(response);

    var actual = usecase.getHistory(principal, SOURCE_ID, OBJECT_ID, FROM, TO, 1000, false);

    assertSame(response, actual);
    verify(authorizationHelper).assertMember(principal, SOURCE_ID, OBJECT_ID);
    verify(service).getEventHistoryBucketed(SOURCE_ID, OBJECT_ID, FROM, TO, 1000);
  }

  @Test
  void getHistory_asLineDelegatesToService() throws PersistenceException {
    var principal = principal();
    var response = mock(EventHistoryResponse.class);
    when(service.getEventHistoryAsLine(SOURCE_ID, OBJECT_ID, FROM, TO, 1000)).thenReturn(response);

    var actual = usecase.getHistory(principal, SOURCE_ID, OBJECT_ID, FROM, TO, 1000, true);

    assertSame(response, actual);
    verify(authorizationHelper).assertMember(principal, SOURCE_ID, OBJECT_ID);
    verify(service).getEventHistoryAsLine(SOURCE_ID, OBJECT_ID, FROM, TO, 1000);
  }

  @Test
  void getHistory_whenMaxPointsTooHigh_capsValueBeforeDelegating() throws PersistenceException {
    var principal = principal();
    var response = mock(EventHistoryResponse.class);
    when(service.getEventHistoryBucketed(SOURCE_ID, OBJECT_ID, FROM, TO, 2000))
        .thenReturn(response);

    var actual = usecase.getHistory(principal, SOURCE_ID, OBJECT_ID, FROM, TO, 5000, false);

    assertSame(response, actual);
    verify(authorizationHelper).assertMember(principal, SOURCE_ID, OBJECT_ID);
    verify(service).getEventHistoryBucketed(SOURCE_ID, OBJECT_ID, FROM, TO, 2000);
  }

  @Test
  void getHistory_whenPersistenceFails_throwsInternalServerError() throws PersistenceException {
    var principal = principal();
    when(service.getEventHistoryBucketed(SOURCE_ID, OBJECT_ID, FROM, TO, 1000))
        .thenThrow(new PersistenceException("db failure"));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.getHistory(principal, SOURCE_ID, OBJECT_ID, FROM, TO, 1000, false));

    assertEquals("CORE-0000", exception.getCode());
    assertEquals("db failure", exception.getMessage());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
    verify(authorizationHelper).assertMember(principal, SOURCE_ID, OBJECT_ID);
  }

  @Test
  void getHistory_whenAuthorizationFails_propagatesException() {
    var principal = principal();
    var exception =
        new GlobalOpenCopException(
            "User is not authorized to view attachments on this event", HttpStatus.FORBIDDEN);
    doThrow(exception).when(authorizationHelper).assertMember(principal, SOURCE_ID, OBJECT_ID);

    var actual =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.getHistory(principal, SOURCE_ID, OBJECT_ID, FROM, TO, 1000, false));

    assertSame(exception, actual);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
