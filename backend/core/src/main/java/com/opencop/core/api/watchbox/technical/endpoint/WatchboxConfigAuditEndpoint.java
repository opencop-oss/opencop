// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditEntry;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditQuery;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditReader;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Watchboxes")
@PreAuthorize("hasRole('admin')")
public class WatchboxConfigAuditEndpoint {

  private final WatchboxConfigAuditReader reader;

  public WatchboxConfigAuditEndpoint(WatchboxConfigAuditReader reader) {
    this.reader = reader;
  }

  @GetMapping("/v1/admin/watchboxes/config-history")
  @Operation(
      summary = "Read Watchbox configuration history",
      description =
          "Results use fixed newest-first chronology. Both occurrence-time bounds are inclusive;"
              + " adjacent closed intervals may both contain their shared boundary. Re-enabling"
              + " the Watchbox runtime feature advances active Watchbox tracking generations"
              + " without adding one history row per Watchbox; that initiating action appears in"
              + " Runtime Feature history.")
  public ResponseEntity<PageResponse<WatchboxConfigAuditResponse>> history(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(required = false) UUID watchboxId,
      @RequestParam(required = false) WatchboxConfigAuditEntry.Operation operation,
      @RequestParam(required = false) UUID actorUserId,
      @Parameter(description = "Inclusive lower bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredFrom,
      @Parameter(description = "Inclusive upper bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredTo) {
    validateQuery(page, size, occurredFrom, occurredTo);
    var query =
        new WatchboxConfigAuditQuery(
            watchboxId, operation, actorUserId, occurredFrom, occurredTo, page, size);
    var entries = reader.findPage(query);
    return ResponseEntity.ok(
        new PageResponse<>(
            entries.content().stream().map(WatchboxConfigAuditResponse::from).toList(),
            entries.page(),
            entries.size(),
            entries.totalElements(),
            entries.totalPages()));
  }

  private static void validateQuery(int page, int size, Instant occurredFrom, Instant occurredTo) {
    if (page < 0
        || size < 1
        || size > WatchboxConfigAuditQuery.MAX_PAGE_SIZE
        || (long) page * size > Integer.MAX_VALUE) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_REQUEST,
          "History page and size exceed the supported range; size must be between 1 and "
              + WatchboxConfigAuditQuery.MAX_PAGE_SIZE,
          HttpStatus.BAD_REQUEST,
          List.of("page", "size"));
    }
    if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_REQUEST,
          "History occurredFrom must not be after occurredTo",
          HttpStatus.BAD_REQUEST,
          List.of("occurredFrom", "occurredTo"));
    }
  }
}
