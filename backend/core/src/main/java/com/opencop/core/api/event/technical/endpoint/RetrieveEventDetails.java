// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.INTERNAL_ERROR;

import com.opencop.core.api.event.ports.business.usecase.RetrieveEventDetailHandler;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrieveEventDetails {

  private final RetrieveEventDetailHandler handler;

  RetrieveEventDetails(final RetrieveEventDetailHandler handler) {
    this.handler = handler;
  }

  @GetMapping("/v1/events/{id}/details")
  public ResponseEntity<OpenCopProperties> getEventDetails(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @PathVariable UUID id) {
    return ok(() -> handler.getDetails(authentication, id));
  }

  @GetMapping("/v1/events/{sourceId}/object/{objectId}/details/latest")
  public ResponseEntity<OpenCopProperties> getLatestEventDetails(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId) {
    return ok(() -> handler.getLatestDetails(authentication, sourceId, objectId));
  }

  /** Translates the gateway's failures into statuses, which is the only thing HTTP owns here. */
  private static ResponseEntity<OpenCopProperties> ok(Supplier<OpenCopProperties> read) {
    try {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(read.get());
    } catch (NotFoundException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(
          INTERNAL_ERROR.code(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** {@link java.util.function.Supplier} cannot declare the gateway's checked failure. */
  @FunctionalInterface
  private interface Supplier<T> {
    T get() throws PersistenceException;
  }
}
