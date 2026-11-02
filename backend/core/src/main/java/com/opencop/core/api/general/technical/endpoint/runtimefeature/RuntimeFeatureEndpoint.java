// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_REQUEST;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.api.general.ports.usecase.runtimefeature.ManageRuntimeFeatures;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryEntry;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryQuery;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureHistoryReader;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Metadata")
@PreAuthorize("hasRole('admin')")
public class RuntimeFeatureEndpoint {

  /**
   * Bounds one reconciliation. A build's manifest is a handful of names; anything approaching this
   * is a malformed or hostile caller, and each name costs its own transaction.
   */
  private static final int MAX_RECONCILE_NAMES = 200;

  private final ManageRuntimeFeatures manageRuntimeFeatures;
  private final RuntimeFeatureHistoryReader historyReader;

  public RuntimeFeatureEndpoint(
      ManageRuntimeFeatures manageRuntimeFeatures, RuntimeFeatureHistoryReader historyReader) {
    this.manageRuntimeFeatures = manageRuntimeFeatures;
    this.historyReader = historyReader;
  }

  @PatchMapping("/v1/admin/runtime-features/{name}")
  @Operation(summary = "Replace a registered runtime feature audience")
  @ApiResponse(responseCode = "200", description = "Canonical current runtime feature state")
  @ApiResponse(
      responseCode = "400",
      description =
          "Unknown fields, an invalid audience shape or mode, or a negative expectedRevision"
              + " return RUNTIME_FEATURE_INVALID_REQUEST; missing USER_GROUPS targets return"
              + " RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS; a body that cannot be parsed as"
              + " JSON returns INVALID_REQUEST",
      content =
          @Content(
              examples = {
                @ExampleObject(
                    name = "Invalid request",
                    value = RuntimeFeatureOpenApiExamples.INVALID_REQUEST_RESPONSE),
                @ExampleObject(
                    name = "Unparseable body",
                    value = RuntimeFeatureOpenApiExamples.UNPARSEABLE_REQUEST_RESPONSE)
              }))
  @ApiResponse(responseCode = "404", description = "Unknown registered feature")
  @ApiResponse(
      responseCode = "409",
      description =
          "Supplied revision is stale, or the runtime feature or one of its target groups is"
              + " locked by a concurrent operation")
  public RuntimeFeatureRecordResponse replaceAudience(
      @PathVariable String name,
      // Fully qualified because Swagger's @RequestBody collides with Spring's import.
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = RuntimeFeaturePatchRequest.class),
                      examples = {
                        @ExampleObject(
                            name = "Disable RTSP",
                            value = RuntimeFeatureOpenApiExamples.DISABLE_RTSP_REQUEST),
                        @ExampleObject(
                            name = "Enable Watchbox for everybody",
                            description =
                                "expectedRevision is optional; omit it for last-write-wins.",
                            value = RuntimeFeatureOpenApiExamples.ENABLE_WATCHBOX_FOR_ALL_REQUEST),
                        @ExampleObject(
                            name = "Enable Watchbox for user groups",
                            value =
                                RuntimeFeatureOpenApiExamples
                                    .ENABLE_WATCHBOX_FOR_USER_GROUPS_REQUEST)
                      }))
          @RequestBody(required = false)
          RuntimeFeaturePatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    var command = RuntimeFeaturePatchMapper.toCommand(request);
    var actor = actor(principal);
    return RuntimeFeatureResponseMapper.toRecord(
        manageRuntimeFeatures.replaceAudience(
            name, command.audience(), command.expectedRevision(), actor));
  }

  @PostMapping("/v1/admin/runtime-features")
  @Operation(
      summary = "Register a frontend-only runtime feature by name",
      description =
          "Creates the definition only when the name is free, always as FRONTEND_ONLY with"
              + " audience NONE at revision 0. The caller supplies nothing but the name; consumer"
              + " class, audience, and revision are server-owned, and fields outside this contract"
              + " are ignored rather than rejected because they cannot reach the registered state."
              + " An existing frontend-only name is left untouched and returned as-is. To converge"
              + " a whole declared manifest in one call, use /v1/admin/runtime-features/reconcile"
              + " rather than calling this once per name.")
  @ApiResponse(responseCode = "201", description = "Definition created by this request")
  @ApiResponse(
      responseCode = "200",
      description = "Name was already registered; the stored definition is returned unchanged")
  @ApiResponse(
      responseCode = "400",
      description =
          "A missing, malformed, or over-long name returns RUNTIME_FEATURE_INVALID_NAME; a name"
              + " reserved for backend enforcement returns RUNTIME_FEATURE_RESERVED_NAME; an"
              + " entirely absent request body returns RUNTIME_FEATURE_INVALID_REQUEST")
  @ApiResponse(
      responseCode = "409",
      description =
          "The name already exists as a BACKEND_ENFORCED definition"
              + " (RUNTIME_FEATURE_CLASS_CONFLICT), or the catalog is locked by a concurrent"
              + " operation (RUNTIME_FEATURE_BUSY)")
  public ResponseEntity<RuntimeFeatureRecordResponse> register(
      // Fully qualified because Swagger's @RequestBody collides with Spring's import.
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = RuntimeFeatureRegisterRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Register a frontend flag",
                              value = RuntimeFeatureOpenApiExamples.REGISTER_REQUEST)))
          @RequestBody(required = false)
          RuntimeFeatureRegisterRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    if (request == null) {
      throw new RuntimeFeatureException(
          RUNTIME_FEATURE_INVALID_REQUEST,
          "Invalid runtime feature registration request",
          HttpStatus.BAD_REQUEST);
    }
    var actor = actor(principal);
    var result = manageRuntimeFeatures.register(request.getName(), actor);
    var response = RuntimeFeatureResponseMapper.toRecord(result.state());
    // The name passed the wire grammar before the catalog was touched, so it needs no escaping.
    return switch (result) {
      case RuntimeFeatureRegistrationResult.Created _ ->
          ResponseEntity.created(URI.create("/v1/admin/runtime-features/" + response.name()))
              .body(response);
      case RuntimeFeatureRegistrationResult.AlreadyExists _ -> ResponseEntity.ok(response);
    };
  }

  @PostMapping("/v1/admin/runtime-features/reconcile")
  @Operation(
      summary = "Register every absent name from a client's declared manifest",
      description =
          "Converges the catalog with the set of names a build declares, in one call. Each name is"
              + " attempted independently and reported as created, already-registered, or rejected,"
              + " so one malformed or backend-owned entry cannot block the rest and every name that"
              + " did register stays registered. Safe to re-run: converged names are no-ops. Like"
              + " the single-name POST, this never enables anything — created definitions start at"
              + " audience NONE and are switched on only through PATCH.")
  @ApiResponse(
      responseCode = "200",
      description =
          "Per-name outcomes. Returned even when some names were rejected — inspect `complete` and"
              + " `rejected` rather than the status code",
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Partial reconciliation",
                      value = RuntimeFeatureOpenApiExamples.RECONCILE_PARTIAL_RESPONSE)))
  @ApiResponse(
      responseCode = "400",
      description =
          "An absent or empty name list, a list containing a null entry, or more than "
              + MAX_RECONCILE_NAMES
              + " names, returns RUNTIME_FEATURE_INVALID_REQUEST. A name that is individually"
              + " malformed or reserved is not a 400 — it comes back in `rejected`")
  public RuntimeFeatureReconcileResponse reconcile(
      // Fully qualified because Swagger's @RequestBody collides with Spring's import.
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = RuntimeFeatureReconcileRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Reconcile a build's manifest",
                              value = RuntimeFeatureOpenApiExamples.RECONCILE_REQUEST)))
          @RequestBody(required = false)
          RuntimeFeatureReconcileRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    var names = request == null ? null : request.getNames();
    if (names == null || names.isEmpty()) {
      throw new RuntimeFeatureException(
          RUNTIME_FEATURE_INVALID_REQUEST,
          "At least one runtime feature name is required",
          HttpStatus.BAD_REQUEST);
    }
    if (names.size() > MAX_RECONCILE_NAMES) {
      throw new RuntimeFeatureException(
          RUNTIME_FEATURE_INVALID_REQUEST,
          "At most " + MAX_RECONCILE_NAMES + " runtime feature names may be reconciled per call",
          HttpStatus.BAD_REQUEST);
    }
    // A null entry is a malformed envelope, not a per-name rejection: the report keys rejections by
    // name, so there is nothing to report it under.
    if (names.contains(null)) {
      throw new RuntimeFeatureException(
          RUNTIME_FEATURE_INVALID_REQUEST,
          "Runtime feature names must not contain null entries",
          HttpStatus.BAD_REQUEST);
    }
    var actor = actor(principal);
    return RuntimeFeatureResponseMapper.toReconcileResponse(
        manageRuntimeFeatures.reconcile(names, actor));
  }

  @GetMapping("/v1/admin/runtime-features/history")
  @Operation(
      summary = "Read runtime feature action history",
      description =
          "Results use fixed newest-first chronology. Both occurrence-time bounds are inclusive;"
              + " adjacent closed intervals may both contain their shared boundary.")
  public ResponseEntity<PageResponse<RuntimeFeatureHistoryResponse>> history(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(required = false) String featureName,
      @RequestParam(required = false) RuntimeFeatureHistoryEntry.Operation operation,
      @RequestParam(required = false) UUID actorUserId,
      @Parameter(description = "Inclusive lower bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredFrom,
      @Parameter(description = "Inclusive upper bound for the event occurrence time")
          @RequestParam(required = false)
          Instant occurredTo) {
    validateHistoryQuery(page, size, featureName, occurredFrom, occurredTo);
    var entries =
        historyReader.findPage(
            new RuntimeFeatureHistoryQuery(
                featureName, operation, actorUserId, occurredFrom, occurredTo, page, size));
    return ResponseEntity.ok(
        new PageResponse<>(
            entries.content().stream().map(RuntimeFeatureHistoryResponse::from).toList(),
            entries.page(),
            entries.size(),
            entries.totalElements(),
            entries.totalPages()));
  }

  private static void validateHistoryQuery(
      int page, int size, String featureName, Instant occurredFrom, Instant occurredTo) {
    if (page < 0
        || size < 1
        || size > RuntimeFeatureHistoryQuery.MAX_PAGE_SIZE
        || (long) page * size > Integer.MAX_VALUE) {
      throw invalidHistoryQuery(
          "History page and size exceed the supported range; size must be between 1 and "
              + RuntimeFeatureHistoryQuery.MAX_PAGE_SIZE);
    }
    if (featureName != null && (featureName.isBlank() || featureName.length() > 64)) {
      throw invalidHistoryQuery("History featureName must contain between 1 and 64 characters");
    }
    if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
      throw invalidHistoryQuery("History occurredFrom must not be after occurredTo");
    }
  }

  private static RuntimeFeatureException invalidHistoryQuery(String message) {
    return new RuntimeFeatureException(
        RUNTIME_FEATURE_INVALID_REQUEST, message, HttpStatus.BAD_REQUEST);
  }

  private static RuntimeFeatureActor actor(OpenCopPrincipal principal) {
    return new RuntimeFeatureActor(
        principal.internalUserId(),
        Optional.ofNullable(principal.fullName()),
        Optional.ofNullable(principal.email()),
        principal.isGlobalAdmin());
  }
}
