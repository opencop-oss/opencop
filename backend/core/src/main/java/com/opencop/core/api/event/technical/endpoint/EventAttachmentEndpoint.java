// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.PrepareAttachmentStream;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events/{sourceId}/{objectId}/attachments")
public class EventAttachmentEndpoint {

  public record StreamPrepareResponse(String hlsUrl) {}

  private final EventAttachmentCreateRegistry createRegistry;
  private final EventAttachmentGetRegistry getRegistry;
  private final EventAttachmentDeleteRegistry deleteRegistry;
  private final PrepareAttachmentStream prepareAttachmentStream;

  EventAttachmentEndpoint(
      EventAttachmentCreateRegistry createRegistry,
      EventAttachmentGetRegistry getRegistry,
      EventAttachmentDeleteRegistry deleteRegistry,
      PrepareAttachmentStream prepareAttachmentStream) {
    this.createRegistry = createRegistry;
    this.getRegistry = getRegistry;
    this.deleteRegistry = deleteRegistry;
    this.prepareAttachmentStream = prepareAttachmentStream;
  }

  @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<AttachmentView> create(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      HttpServletRequest request)
      throws IOException {

    var view = createRegistry.create(authentication, sourceId, objectId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(view);
  }

  @GetMapping
  public ResponseEntity<List<AttachmentView>> list(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId) {

    return ResponseEntity.ok(getRegistry.list(authentication, sourceId, objectId));
  }

  @GetMapping("/{attachmentId}")
  public ResponseEntity<AttachmentView> get(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @PathVariable UUID attachmentId) {

    return ResponseEntity.ok(getRegistry.get(authentication, sourceId, objectId, attachmentId));
  }

  @DeleteMapping("/{attachmentId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @PathVariable UUID attachmentId) {

    deleteRegistry.delete(authentication, sourceId, objectId, attachmentId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{attachmentId}/stream/prepare")
  public ResponseEntity<StreamPrepareResponse> prepareStream(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @PathVariable UUID attachmentId) {

    return prepareAttachmentStream
        .prepare(authentication, sourceId, objectId, attachmentId)
        .map(hlsUrl -> ResponseEntity.ok(new StreamPrepareResponse(hlsUrl)))
        .orElseThrow(
            () ->
                new GlobalOpenCopException(
                    "STREAM_PREPARATION_UNAVAILABLE",
                    "Stream preparation is currently unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE));
  }
}
