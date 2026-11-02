// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.ports.business.usecase.RemarkHandler;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.model.opencop.CreateRemarkRequest;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import com.opencop.core.sdk.technical.model.opencop.UpdateRemarkRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RemarksEndpoint {

  private final RemarkHandler handler;

  RemarksEndpoint(RemarkHandler handler) {
    this.handler = handler;
  }

  @PostMapping("/v1/events/{sourceId}/{objectId}/remarks")
  public ResponseEntity<HttpStatus> addRemark(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @RequestBody CreateRemarkRequest remarkRequest) {
    final UUID userId = authentication.internalUserId();

    try {
      this.handler.addRemark(userId, sourceId, objectId, remarkRequest);
      return ResponseEntity.ok().build();
    } catch (GlobalOpenCopException e) {
      return ResponseEntity.status(e.getHttpStatus()).build();
    }
  }

  @GetMapping("/v1/events/{sourceId}/{objectId}/remarks")
  public ResponseEntity<List<Remark>> retrieveRemarks(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId) {
    try {
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(this.handler.retrieveRemarks(authentication.internalUserId(), sourceId, objectId));
    } catch (GlobalOpenCopException e) {
      return ResponseEntity.status(e.getHttpStatus()).build();
    }
  }

  @PatchMapping("/v1/events/{sourceId}/{objectId}/remarks")
  public ResponseEntity<HttpStatus> updateRemark(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @RequestBody UpdateRemarkRequest updateRemarkRequest) {
    try {
      this.handler.updateRemark(
          authentication.internalUserId(), sourceId, objectId, updateRemarkRequest);
      return ResponseEntity.ok().build();
    } catch (GlobalOpenCopException e) {
      return ResponseEntity.status(e.getHttpStatus()).build();
    }
  }

  @DeleteMapping("/v1/events/{sourceId}/{objectId}/remarks/{id}")
  public ResponseEntity<HttpStatus> deleteRemark(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @PathVariable String objectId,
      @PathVariable UUID id) {
    try {
      this.handler.deleteRemark(authentication.internalUserId(), sourceId, objectId, id);
      return ResponseEntity.ok().build();
    } catch (GlobalOpenCopException e) {
      return ResponseEntity.status(e.getHttpStatus()).build();
    }
  }
}
