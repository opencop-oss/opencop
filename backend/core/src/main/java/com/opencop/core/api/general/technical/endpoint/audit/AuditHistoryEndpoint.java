// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.audit;

import com.opencop.core.api.general.business.model.audit.AuditEntry;
import com.opencop.core.api.general.business.model.audit.AuditQuery;
import com.opencop.core.api.general.ports.usecase.audit.ReadAuditHistory;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads the shared resource audit ledger.
 *
 * <p>Global administrators only, independently of who can see the resources themselves: the filters
 * and exact totals here would otherwise disclose entities the caller cannot read.
 */
@RestController
@Tag(name = "Audit history")
@PreAuthorize("hasRole('admin')")
public class AuditHistoryEndpoint {

  private final ReadAuditHistory history;

  public AuditHistoryEndpoint(ReadAuditHistory history) {
    this.history = history;
  }

  @GetMapping("/v1/admin/audit-history")
  @Operation(
      summary = "Read resource audit history",
      description =
          "Successful user actions on data sources, layers, layer groups and mapper profiles."
              + " Results use fixed newest-first chronology and cannot be reordered by the caller."
              + " Both occurrence-time bounds are inclusive, so adjacent closed intervals may each"
              + " contain their shared boundary. Paging is by offset and is not snapshot"
              + " consistent: rows written between requests shift later offsets. Watchbox and"
              + " runtime feature history live in their own endpoints.")
  public ResponseEntity<PageResponse<AuditHistoryResponse>> history(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + AuditQuery.DEFAULT_SIZE) int size,
      @RequestParam(required = false) AuditEntry.EntityType entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) AuditEntry.Operation operation,
      @RequestParam(required = false) UUID actorUserId,
      @Parameter(description = "Inclusive lower bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredFrom,
      @Parameter(description = "Inclusive upper bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredTo) {
    var result =
        this.history.read(
            new AuditQuery(
                entityType,
                entityId,
                operation,
                actorUserId,
                occurredFrom,
                occurredTo,
                page,
                size));

    var totalPages = (int) Math.ceil((double) result.totalElements() / size);
    return ResponseEntity.ok(
        new PageResponse<>(
            result.content().stream().map(AuditHistoryResponse::from).toList(),
            page,
            size,
            result.totalElements(),
            totalPages));
  }
}
