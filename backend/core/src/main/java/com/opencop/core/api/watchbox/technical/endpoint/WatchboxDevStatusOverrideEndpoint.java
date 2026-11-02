// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxDevStatusOverrides;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "opencop.dev.mode", havingValue = "true", matchIfMissing = false)
@PreAuthorize("hasRole('admin')")
@Tag(name = "Watchboxes")
public class WatchboxDevStatusOverrideEndpoint {

  private static final Logger log =
      LoggerFactory.getLogger(WatchboxDevStatusOverrideEndpoint.class);

  private final ManageWatchboxes usecase;
  private final WatchboxDevStatusOverrides overrideService;

  public WatchboxDevStatusOverrideEndpoint(
      ManageWatchboxes usecase, WatchboxDevStatusOverrides overrideService) {
    this.usecase = usecase;
    this.overrideService = overrideService;
  }

  @Operation(
      summary = "Set a dev-only Watchbox status override",
      description =
          "Local rendering fixture enabled only when opencop.dev.mode=true. Overrides are "
              + "process-local, in-memory, and may intentionally create status combinations that "
              + "normal processor state would not emit.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Watchbox returned with the override applied.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxResponse.class),
                examples =
                    @ExampleObject(
                        name = "Dev override response",
                        value = WatchboxOpenApiExamples.DEV_OVERRIDE_RESPONSE))),
    @ApiResponse(
        responseCode = "400",
        description = "Override request is invalid.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Invalid override request",
                      value = WatchboxOpenApiExamples.INVALID_DEV_OVERRIDE),
                  @ExampleObject(
                      name = "Malformed request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST_MALFORMED)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "Watchbox not found.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Watchbox not found",
                        value = WatchboxOpenApiExamples.NOT_FOUND)))
  })
  @PreAuthorize("hasRole('admin')")
  @PutMapping("/v1/dev/watchboxes/{watchboxId}/status-override")
  public ResponseEntity<WatchboxResponse> set(
      @PathVariable UUID watchboxId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = WatchboxStatusOverrideRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Set dev override",
                              value = WatchboxOpenApiExamples.DEV_OVERRIDE_REQUEST)))
          @RequestBody
          WatchboxStatusOverrideRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    WatchboxResponse visibleWatchbox = usecase.get(watchboxId, caller(authentication));
    WatchboxStatusOverrideRequest normalized = overrideService.set(watchboxId, request);
    log.info(
        "WATCHBOX_DEV_OVERRIDE operation=set watchbox_id={} actor_user_id={} alerting_status={} source_status_count={}",
        watchboxId,
        authentication.internalUserId(),
        normalized.alertingStatus(),
        normalized.sourceStatuses().size());
    // The dev endpoint is admin-only at path and method, so the caller is always a manager.
    return ResponseEntity.ok(overrideService.apply(visibleWatchbox, true));
  }

  @Operation(summary = "Get a dev-only Watchbox status override")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Stored override request.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxStatusOverrideRequest.class),
                examples =
                    @ExampleObject(
                        name = "Stored dev override",
                        value = WatchboxOpenApiExamples.DEV_OVERRIDE_REQUEST))),
    @ApiResponse(
        responseCode = "404",
        description = "Watchbox or override not found.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Watchbox or override not found",
                        value = WatchboxOpenApiExamples.NOT_FOUND)))
  })
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/dev/watchboxes/{watchboxId}/status-override")
  public ResponseEntity<WatchboxStatusOverrideRequest> get(
      @PathVariable UUID watchboxId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    usecase.get(watchboxId, caller(authentication));
    return ResponseEntity.ok(overrideService.get(watchboxId).orElseThrow(this::notFound));
  }

  @Operation(summary = "Clear a dev-only Watchbox status override")
  @ApiResponse(responseCode = "204", description = "Override cleared or was already absent.")
  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/v1/dev/watchboxes/{watchboxId}/status-override")
  public ResponseEntity<Void> delete(
      @PathVariable UUID watchboxId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    // Dev-only cleanup is intentionally idempotent by ID, including after a Watchbox is gone.
    overrideService.clear(watchboxId);
    log.info(
        "WATCHBOX_DEV_OVERRIDE operation=clear watchbox_id={} actor_user_id={}",
        watchboxId,
        authentication.internalUserId());
    return ResponseEntity.noContent().build();
  }

  private WatchboxException notFound() {
    return new WatchboxException(
        WatchboxErrorCode.WATCHBOX_NOT_FOUND,
        "Watchbox status override not found",
        HttpStatus.NOT_FOUND,
        List.of(),
        List.of());
  }

  private static WatchboxCaller caller(OpenCopPrincipal authentication) {
    return new WatchboxCaller(
        authentication.internalUserId(),
        authentication.isGlobalAdmin(),
        authentication.fullName(),
        authentication.email());
  }
}
