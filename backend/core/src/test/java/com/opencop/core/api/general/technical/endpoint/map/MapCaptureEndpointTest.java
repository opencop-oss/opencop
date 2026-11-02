// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.api.general.business.model.map.MapView;
import com.opencop.core.api.general.ports.gateway.map.MapCaptureService;
import com.opencop.core.api.general.technical.gateway.db.map.MapCapturePersistenceException;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MapCaptureEndpointTest {

  @Mock private MapCaptureService service;

  private MapCaptureEndpoint endpoint;

  @BeforeEach
  void setUp() {
    endpoint = new MapCaptureEndpoint(service);
  }

  @Test
  void getById_returnsCapture() throws PersistenceException {
    var captureId = UUID.randomUUID();
    var capture = new MapCapture(captureId, "One", new MapView(1f, 2f, 3f, 4f, 5f), null, null);
    when(service.findById(captureId)).thenReturn(capture);

    var response = endpoint.getOne(captureId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(capture, response.getBody());
  }

  @Test
  void getById_whenMissing_throwsNotFound() throws PersistenceException {
    var captureId = UUID.randomUUID();
    when(service.findById(captureId)).thenThrow(new PersistenceException("Missing"));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.getOne(captureId));

    assertEquals("Missing", error.getMessage());
    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
  }

  @Test
  void getAllForUser_returnsCaptures() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captures =
        List.of(
            new MapCapture(
                UUID.randomUUID(), "First", new MapView(1f, 2f, 3f, 4f, 5f), null, null));
    when(service.findAll(userId)).thenReturn(captures);

    var response = endpoint.getAll(principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(captures, response.getBody());
    verify(service).findAll(userId);
  }

  @Test
  void getAllForUser_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    when(service.findAll(userId)).thenThrow(new PersistenceException("Bad query"));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.getAll(principal));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Bad query", error.getMessage());
  }

  @Test
  void save_returnsCreatedWithLocation() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var createdId = UUID.randomUUID();
    var body = new MapCapture(null, "New", new MapView(1f, 2f, 3f, 4f, 5f), null, null);
    var created = new MapCapture(createdId, "New", new MapView(1f, 2f, 3f, 4f, 5f), null, null);
    when(service.create(userId, body)).thenReturn(created);

    var response = endpoint.save(principal, body);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(
        URI.create("/v1/me/map/captures/" + createdId), response.getHeaders().getLocation());
    assertEquals(created, response.getBody());
  }

  @Test
  void save_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var body = new MapCapture(null, "New", new MapView(1f, 2f, 3f, 4f, 5f), null, null);
    when(service.create(userId, body)).thenThrow(new PersistenceException("Invalid"));

    var error = assertThrows(GlobalOpenCopException.class, () -> endpoint.save(principal, body));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Invalid", error.getMessage());
  }

  @Test
  void patch_returnsUpdatedCapture() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captureId = UUID.randomUUID();
    var patch = new MapCapturePatch(Optional.of("Updated"), Optional.empty());
    var updated = new MapCapture(captureId, "Updated", new MapView(1f, 2f, 3f, 4f, 5f), null, null);
    when(service.patch(captureId, patch, userId)).thenReturn(updated);

    var response = endpoint.patch(principal, captureId, patch);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(updated, response.getBody());
  }

  @Test
  void patch_whenNotFound_throwsNotFound() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captureId = UUID.randomUUID();
    var patch = new MapCapturePatch(Optional.empty(), Optional.empty());
    when(service.patch(captureId, patch, userId))
        .thenThrow(new MapCapturePersistenceException.NotFound("Missing"));

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> endpoint.patch(principal, captureId, patch));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    assertEquals("Missing", error.getMessage());
  }

  @Test
  void patch_whenPersistenceFails_throwsBadRequest() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captureId = UUID.randomUUID();
    var patch = new MapCapturePatch(Optional.empty(), Optional.empty());
    when(service.patch(captureId, patch, userId))
        .thenThrow(new PersistenceException("Invalid patch"));

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> endpoint.patch(principal, captureId, patch));

    assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    assertEquals("Invalid patch", error.getMessage());
  }

  @Test
  void delete_whenSuccessful_returnsNoContent() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captureId = UUID.randomUUID();

    var response = endpoint.delete(principal, captureId);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertNull(response.getBody());
    verify(service).delete(captureId, userId);
  }

  @Test
  void delete_whenPersistenceFails_throwsNotFound() throws PersistenceException {
    var userId = UUID.randomUUID();
    var principal = principal(userId);
    var captureId = UUID.randomUUID();
    doThrow(new PersistenceException("Missing")).when(service).delete(captureId, userId);

    var error =
        assertThrows(GlobalOpenCopException.class, () -> endpoint.delete(principal, captureId));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    assertEquals("Missing", error.getMessage());
  }

  private static OpenCopPrincipal principal(UUID userId) {
    return TestPrincipals.mockPrincipal(userId);
  }
}
