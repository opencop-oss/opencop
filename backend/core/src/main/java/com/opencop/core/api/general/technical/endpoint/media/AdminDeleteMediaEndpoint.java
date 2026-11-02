// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Admin-only for now. Owner-initiated delete is deferred — media may be referenced by other
// entities (e.g., event details, user profiles) and deleting would orphan those references.
// Needs a usage-check or soft-delete strategy before exposing to non-admins.
@RestController
public class AdminDeleteMediaEndpoint {

  private final DeleteMedia usecase;

  AdminDeleteMediaEndpoint(DeleteMedia usecase) {
    this.usecase = usecase;
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/v1/admin/media/{mediaId}")
  public ResponseEntity<Void> delete(@PathVariable UUID mediaId) {
    usecase.delete(mediaId);
    return ResponseEntity.noContent().build();
  }
}
