// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.map;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.api.general.ports.gateway.map.MapCaptureService;
import com.opencop.core.api.general.technical.gateway.db.map.MapCapturePersistenceException;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
public class MapCaptureEndpoint {

  private final MapCaptureService service;

  MapCaptureEndpoint(MapCaptureService service) {
    this.service = service;
  }

  @GetMapping("/v1/map/captures/{captureId}")
  public ResponseEntity<MapCapture> get(@PathVariable UUID captureId) {
    try {
      var capture = this.service.findById(captureId);
      return ResponseEntity.ok(capture);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @GetMapping("/v1/me/map/captures/{captureId}")
  public ResponseEntity<MapCapture> getOne(@PathVariable UUID captureId) {
    try {
      var capture = this.service.findById(captureId);
      return ResponseEntity.ok(capture);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @GetMapping("/v1/me/map/captures")
  public ResponseEntity<List<MapCapture>> getAll(
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    try {
      var captures = this.service.findAll(authentication.internalUserId());
      return ResponseEntity.ok(captures);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/v1/me/map/captures")
  public ResponseEntity<MapCapture> save(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @RequestBody MapCapture body) {
    try {
      var created = this.service.create(authentication.internalUserId(), body);
      return ResponseEntity.created(URI.create("/v1/me/map/captures/" + created.id()))
          .body(created);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PatchMapping("/v1/me/map/captures/{captureId}")
  public ResponseEntity<MapCapture> patch(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID captureId,
      @RequestBody MapCapturePatch body) {
    try {
      return ResponseEntity.ok(
          this.service.patch(captureId, body, authentication.internalUserId()));
    } catch (MapCapturePersistenceException.NotFound e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @DeleteMapping("/v1/me/map/captures/{captureId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal OpenCopPrincipal authentication, @PathVariable UUID captureId) {
    try {
      this.service.delete(captureId, authentication.internalUserId());
      return ResponseEntity.noContent().build();
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }
}
