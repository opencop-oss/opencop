// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.business.usecase.RemarkService;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.model.opencop.CreateRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RemarkUsecaseTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final String OBJECT_ID = "event-123";

  @Mock private RemarkService remarkService;
  @Mock private EventAuthorizationPolicy eventAuthorizationPolicy;

  private RemarkUsecase usecase;

  @BeforeEach
  void setUp() {
    usecase = new RemarkUsecase(remarkService, eventAuthorizationPolicy);
  }

  @Test
  void addRemark_passesSourceScopedIdentityToPersistence() {
    usecase.addRemark(USER_ID, SOURCE_ID, OBJECT_ID, new CreateRemarkRequest("alpha"));

    var requestCaptor = ArgumentCaptor.forClass(PersistRemarkRequest.class);
    verify(remarkService).addRemark(requestCaptor.capture());
    assertEquals(SOURCE_ID, requestCaptor.getValue().sourceId());
    assertEquals(OBJECT_ID, requestCaptor.getValue().objectId());
    assertEquals("alpha", requestCaptor.getValue().content());
    assertEquals(USER_ID, requestCaptor.getValue().userId());
    verify(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "add remark");
  }

  @Test
  void addRemark_whenSourceIsArchived_throwsConflictWithoutPersisting() {
    doThrow(new GlobalOpenCopException("archived", HttpStatus.CONFLICT))
        .when(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "add remark");

    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.addRemark(USER_ID, SOURCE_ID, OBJECT_ID, new CreateRemarkRequest("x")));

    assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
    verify(remarkService, never()).addRemark(any());
  }

  @Test
  void addRemark_whenContentBlank_throwsBadRequestWithoutPersisting() {
    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.addRemark(USER_ID, SOURCE_ID, OBJECT_ID, new CreateRemarkRequest(" ")));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    verify(remarkService, never()).addRemark(any());
  }

  @Test
  void retrieveRemarks_usesSourceScopedLookup() throws Exception {
    var expected = List.of(new Remark(UUID.randomUUID(), OBJECT_ID, "note", null, 1L, null));
    when(remarkService.retrieveRemarks(SOURCE_ID, OBJECT_ID)).thenReturn(expected);

    var actual = usecase.retrieveRemarks(USER_ID, SOURCE_ID, OBJECT_ID);

    assertSame(expected, actual);
    verify(eventAuthorizationPolicy).assertMember(USER_ID, SOURCE_ID, OBJECT_ID);
    verify(remarkService).retrieveRemarks(SOURCE_ID, OBJECT_ID);
  }

  @Test
  void updateRemark_whenSourceIsArchived_throwsConflictWithoutPersisting() throws Exception {
    doThrow(new GlobalOpenCopException("archived", HttpStatus.CONFLICT))
        .when(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "update remark");

    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.updateRemark(
                    USER_ID,
                    SOURCE_ID,
                    OBJECT_ID,
                    new UpdateRemarkRequest(UUID.randomUUID(), "x")));

    assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
    verify(remarkService, never()).updateRemark(any(), any());
  }

  @Test
  void updateRemark_whenAuthorized_callsService() throws Exception {
    var request = new UpdateRemarkRequest(UUID.randomUUID(), "updated");

    usecase.updateRemark(USER_ID, SOURCE_ID, OBJECT_ID, request);

    verify(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "update remark");
    verify(remarkService).updateRemark(USER_ID, request);
  }

  @Test
  void deleteRemark_whenSourceIsArchived_throwsConflictWithoutPersisting() throws Exception {
    doThrow(new GlobalOpenCopException("archived", HttpStatus.CONFLICT))
        .when(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "delete remark");

    var error =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.deleteRemark(USER_ID, SOURCE_ID, OBJECT_ID, UUID.randomUUID()));

    assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
    verify(remarkService, never()).deleteRemark(any(), any());
  }

  @Test
  void deleteRemark_whenAuthorized_callsService() throws Exception {
    var remarkId = UUID.randomUUID();

    usecase.deleteRemark(USER_ID, SOURCE_ID, OBJECT_ID, remarkId);

    verify(eventAuthorizationPolicy)
        .assertEditorForActiveResource(USER_ID, SOURCE_ID, OBJECT_ID, "delete remark");
    verify(remarkService).deleteRemark(USER_ID, remarkId);
  }
}
