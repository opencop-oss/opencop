// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_INVALID_FILTER;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_INVALID_REQUEST;

import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxFilterTesting;
import com.opencop.core.sdk.model.watchbox.WatchboxCombine;
import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterOperation;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterTestRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@Tag(name = "Watchboxes")
public class WatchboxesEndpoint {

  private static final Set<String> FILTER_TEST_ROOT_FIELDS = Set.of("filter", "feature");
  private static final Set<String> FILTER_FIELDS = Set.of("combine", "predicates");
  private static final Set<String> PREDICATE_FIELDS =
      Set.of("path", "operation", "values", "negate", "offset", "length", "caseInsensitive");
  private static final Set<String> FILTER_OPERATION_NAMES =
      Arrays.stream(WatchboxFilterOperation.values()).map(Enum::name).collect(Collectors.toSet());
  private static final Set<String> FILTER_COMBINE_NAMES =
      Arrays.stream(WatchboxCombine.values()).map(Enum::name).collect(Collectors.toSet());

  private final ManageWatchboxes usecase;
  private final WatchboxFilterTesting filterTesting;
  private final ObjectMapper objectMapper;

  public WatchboxesEndpoint(
      ManageWatchboxes usecase, WatchboxFilterTesting filterTesting, ObjectMapper objectMapper) {
    this.usecase = usecase;
    this.filterTesting = filterTesting;
    this.objectMapper = objectMapper;
  }

  @Operation(summary = "Create a Watchbox")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Watchbox created.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxResponse.class),
                examples =
                    @ExampleObject(
                        name = "Created Watchbox",
                        value = WatchboxOpenApiExamples.WATCHBOX_CREATE_SUCCESS))),
    @ApiResponse(
        responseCode = "400",
        description = "Watchbox validation failed or the feature flag is disabled.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Feature disabled",
                      value = WatchboxOpenApiExamples.FEATURE_DISABLED),
                  @ExampleObject(
                      name = "Invalid polygon",
                      value = WatchboxOpenApiExamples.INVALID_POLYGON),
                  @ExampleObject(
                      name = "Invalid filter",
                      value = WatchboxOpenApiExamples.INVALID_FILTER),
                  @ExampleObject(
                      name = "Invalid name",
                      value = WatchboxOpenApiExamples.INVALID_NAME),
                  @ExampleObject(
                      name = "Invalid request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST),
                  @ExampleObject(
                      name = "Malformed request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST_MALFORMED)
                })),
    @ApiResponse(
        responseCode = "403",
        description = "Current user can view the layer but cannot manage Watchboxes on it.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Management role required",
                        value = WatchboxOpenApiExamples.MANAGEMENT_ROLE_REQUIRED))),
    @ApiResponse(
        responseCode = "409",
        description =
            "Watchbox runtime eligibility constraints failed, or a selected layer was"
                + " archived concurrently: the commit-time lifecycle conflict returns a"
                + " GlobalErrorResponse with code LIFECYCLE_CONFLICT.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(anyOf = {WatchboxErrorResponse.class, GlobalErrorResponse.class}),
                examples = {
                  @ExampleObject(
                      name = "No eligible sources",
                      value = WatchboxOpenApiExamples.NO_ELIGIBLE_SOURCES),
                  @ExampleObject(
                      name = "Source eligibility failed",
                      value =
                          """
                          {
                            "code": "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
                            "message": "Selected sources require object ID mapping correction",
                            "fields": ["layerIds"],
                            "reasons": [],
                            "context": {
                              "sourceNames": ["admission-source-a", "admission-source-b"]
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "Object ID On Missing is not Error",
                      value = WatchboxOpenApiExamples.OBJECT_ID_ON_MISSING_NOT_ERROR)
                }))
  })
  @PostMapping("/v1/watchboxes")
  public ResponseEntity<WatchboxResponse> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = WatchboxOpenApiSchemas.CreateRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Create Watchbox",
                              value = WatchboxOpenApiExamples.CREATE_REQUEST)))
          @RequestBody(required = false)
          WatchboxCreateRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(usecase.create(request, caller(authentication)));
  }

  @Operation(summary = "List Watchboxes visible to the current user")
  @ApiResponse(
      responseCode = "200",
      description =
          "Visible Watchboxes, including active and degraded examples. Each row carries"
              + " caller-specific capabilities (canPatch/canDelete) derived from the same"
              + " management policy PATCH and DELETE enforce: global-admin bypass, or"
              + " OWNER/ADMIN access on every selected layer.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              array = @ArraySchema(schema = @Schema(implementation = WatchboxResponse.class)),
              examples =
                  @ExampleObject(
                      name = "Watchbox list",
                      value = WatchboxOpenApiExamples.WATCHBOX_LIST)))
  @GetMapping("/v1/watchboxes")
  public ResponseEntity<List<WatchboxResponse>> list(
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(usecase.list(caller(authentication)));
  }

  @Operation(summary = "Test a Watchbox event-property filter against event JSON")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Filter test result.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxFilterMatch.class),
                examples = {
                  @ExampleObject(
                      name = "Hostile symbol match",
                      value = WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_MATCH),
                  @ExampleObject(
                      name = "Concerning symbol match",
                      description =
                          "Negates friendly affiliation, so this broader rule includes symbols"
                              + " that are not identified as friendly.",
                      value = WatchboxOpenApiExamples.FILTER_TEST_RESPONSE_NOT_FRIENDLY_MATCH)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "Request or filter validation failed.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Invalid filter",
                      value = WatchboxOpenApiExamples.INVALID_FILTER),
                  @ExampleObject(
                      name = "Invalid request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST),
                  @ExampleObject(
                      name = "Malformed request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST_MALFORMED)
                }))
  })
  @PostMapping("/v1/watchboxes/filter/test")
  public ResponseEntity<WatchboxFilterMatch> testFilter(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = WatchboxFilterTestRequest.class),
                      examples = {
                        @ExampleObject(
                            name = "Test hostile symbol filter",
                            value = WatchboxOpenApiExamples.FILTER_TEST_REQUEST),
                        @ExampleObject(
                            name = "Test concerning / not friendly symbol filter",
                            description =
                                "Uses negate=true on friendly affiliation, so symbols that are"
                                    + " not identified as friendly match.",
                            value = WatchboxOpenApiExamples.FILTER_TEST_REQUEST_NOT_FRIENDLY)
                      }))
          @RequestBody(required = false)
          JsonNode requestNode,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    validateFilterTestRequestNode(requestNode);
    WatchboxFilterTestRequest request = mapFilterTestRequest(requestNode);
    return ResponseEntity.ok(filterTesting.test(request));
  }

  @Operation(summary = "Get a Watchbox")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Watchbox detail, including source status rows visible to managers and"
                + " caller-specific capabilities (canPatch/canDelete) derived from the same"
                + " management policy PATCH and DELETE enforce.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxResponse.class),
                examples =
                    @ExampleObject(
                        name = "Watchbox detail with source statuses",
                        value = WatchboxOpenApiExamples.WATCHBOX_DETAIL_WITH_SOURCE_STATUS))),
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
  @GetMapping("/v1/watchboxes/{watchboxId}")
  public ResponseEntity<WatchboxResponse> get(
      @PathVariable UUID watchboxId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(usecase.get(watchboxId, caller(authentication)));
  }

  @Operation(
      summary = "Patch a Watchbox",
      description =
          "Requires current-object management authorization: global-admin status, or OWNER/ADMIN"
              + " access on every selected layer — the decision advertised as canPatch on"
              + " list/get responses. canPatch covers that authorization only: payload- and"
              + " state-dependent validation still applies at request time (authorization for"
              + " newly selected layers, runtime feature availability with its cleanup-only"
              + " carve-out, tracking-reset confirmation, layer lifecycle validation), and this"
              + " endpoint remains authoritative when access or state changed after a response"
              + " was read.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Watchbox patched.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxResponse.class),
                examples =
                    @ExampleObject(
                        name = "Patched Watchbox",
                        value = WatchboxOpenApiExamples.WATCHBOX_DETAIL_WITH_SOURCE_STATUS))),
    @ApiResponse(
        responseCode = "400",
        description = "Watchbox validation failed or the feature flag is disabled.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples = {
                  @ExampleObject(
                      name = "Feature disabled",
                      value = WatchboxOpenApiExamples.FEATURE_DISABLED),
                  @ExampleObject(
                      name = "Invalid polygon",
                      value = WatchboxOpenApiExamples.INVALID_POLYGON),
                  @ExampleObject(
                      name = "Invalid filter",
                      value = WatchboxOpenApiExamples.INVALID_FILTER),
                  @ExampleObject(
                      name = "Invalid name",
                      value = WatchboxOpenApiExamples.INVALID_NAME),
                  @ExampleObject(
                      name = "Invalid request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST),
                  @ExampleObject(
                      name = "Malformed request body",
                      value = WatchboxOpenApiExamples.INVALID_REQUEST_MALFORMED)
                })),
    @ApiResponse(
        responseCode = "403",
        description = "Current user can view the Watchbox but cannot manage every selected layer.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Management role required",
                        value = WatchboxOpenApiExamples.MANAGEMENT_ROLE_REQUIRED))),
    @ApiResponse(
        responseCode = "409",
        description =
            "Patch reset or runtime eligibility constraints failed, or a selected layer"
                + " was archived concurrently: the commit-time lifecycle conflict returns a"
                + " GlobalErrorResponse with code LIFECYCLE_CONFLICT.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(anyOf = {WatchboxErrorResponse.class, GlobalErrorResponse.class}),
                examples = {
                  @ExampleObject(
                      name = "Tracking reset confirmation required",
                      value = WatchboxOpenApiExamples.TRACKING_RESET_REQUIRED),
                  @ExampleObject(
                      name = "No eligible sources",
                      value = WatchboxOpenApiExamples.NO_ELIGIBLE_SOURCES),
                  @ExampleObject(
                      name = "Source eligibility failed",
                      value =
                          """
                          {
                            "code": "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
                            "message": "Selected sources require object ID mapping correction",
                            "fields": ["layerIds"],
                            "reasons": [],
                            "context": {
                              "sourceNames": ["admission-source-a", "admission-source-b"]
                            }
                          }
                          """),
                  @ExampleObject(
                      name = "Object ID On Missing is not Error",
                      value = WatchboxOpenApiExamples.OBJECT_ID_ON_MISSING_NOT_ERROR)
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
  @PatchMapping("/v1/watchboxes/{watchboxId}")
  public ResponseEntity<WatchboxResponse> patch(
      @PathVariable UUID watchboxId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = WatchboxOpenApiSchemas.PatchRequest.class),
                      examples =
                          @ExampleObject(
                              name = "Patch with tracking reset confirmation",
                              value = WatchboxOpenApiExamples.PATCH_RESET_REQUEST)))
          @RequestBody(required = false)
          WatchboxPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(usecase.patch(watchboxId, request, caller(authentication)));
  }

  @Operation(
      summary = "Delete a Watchbox",
      description =
          "Requires current-object management authorization: global-admin status, or OWNER/ADMIN"
              + " access on every selected layer — the decision advertised as canDelete on"
              + " list/get responses. The endpoint remains authoritative when access changed"
              + " after a response was read.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Watchbox deleted."),
    @ApiResponse(
        responseCode = "403",
        description = "Current user can view the Watchbox but cannot manage every selected layer.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WatchboxErrorResponse.class),
                examples =
                    @ExampleObject(
                        name = "Management role required",
                        value = WatchboxOpenApiExamples.MANAGEMENT_ROLE_REQUIRED))),
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
  @DeleteMapping("/v1/watchboxes/{watchboxId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID watchboxId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    usecase.delete(watchboxId, caller(authentication));
    return ResponseEntity.noContent().build();
  }

  private static WatchboxCaller caller(OpenCopPrincipal authentication) {
    return new WatchboxCaller(
        authentication.internalUserId(),
        authentication.isGlobalAdmin(),
        authentication.fullName(),
        authentication.email());
  }

  private WatchboxFilterTestRequest mapFilterTestRequest(JsonNode requestNode) {
    try {
      return objectMapper.treeToValue(requestNode, WatchboxFilterTestRequest.class);
    } catch (Exception ex) {
      throw invalidRequest("filter test request is malformed", "body");
    }
  }

  private static void validateFilterTestRequestNode(JsonNode requestNode) {
    if (requestNode == null || requestNode.isNull() || !requestNode.isObject()) {
      throw invalidRequest("request body is required", "body");
    }
    validateAllowedFields(requestNode, FILTER_TEST_ROOT_FIELDS, "body", true);
    JsonNode feature = requestNode.get("feature");
    if (feature == null || feature.isNull()) {
      throw invalidRequest("feature is required", "feature");
    }
    if (!feature.isObject()) {
      throw invalidRequest("feature must be an object", "feature");
    }

    JsonNode filter = requestNode.get("filter");
    if (filter == null || filter.isNull()) {
      return;
    }
    if (!filter.isObject()) {
      throw invalidFilter("filter must be an object", "filter");
    }
    validateAllowedFields(filter, FILTER_FIELDS, "filter", false);

    JsonNode combine = filter.get("combine");
    if (combine != null
        && !combine.isNull()
        && (!combine.isString() || !FILTER_COMBINE_NAMES.contains(combine.asText()))) {
      throw invalidFilter("filter combine is not supported", "filter.combine");
    }

    JsonNode predicates = filter.get("predicates");
    if (predicates == null || predicates.isNull()) {
      return;
    }
    if (!predicates.isArray()) {
      throw invalidFilter("filter predicates must be an array", "filter.predicates");
    }
    for (int i = 0; i < predicates.size(); i++) {
      validatePredicateNode(predicates.get(i), i);
    }
  }

  private static void validatePredicateNode(JsonNode predicate, int index) {
    String fieldPrefix = "filter.predicates[" + index + "]";
    if (predicate == null || predicate.isNull() || !predicate.isObject()) {
      throw invalidFilter("filter predicate is required", fieldPrefix);
    }
    validateAllowedFields(predicate, PREDICATE_FIELDS, fieldPrefix, false);

    JsonNode operation = predicate.get("operation");
    if (operation != null
        && !operation.isNull()
        && (!operation.isString() || !FILTER_OPERATION_NAMES.contains(operation.asText()))) {
      throw invalidFilter("filter operation is not supported", fieldPrefix + ".operation");
    }
  }

  private static void validateAllowedFields(
      JsonNode objectNode, Set<String> allowedFields, String fieldPrefix, boolean requestError) {
    for (var property : objectNode.properties()) {
      if (!allowedFields.contains(property.getKey())) {
        if (requestError) {
          throw invalidRequest(
              "unknown field is not supported", fieldPrefix + "." + property.getKey());
        }
        throw invalidFilter(
            "unknown field is not supported", fieldPrefix + "." + property.getKey());
      }
    }
  }

  private static WatchboxException invalidRequest(String message, String field) {
    return new WatchboxException(
        WATCHBOX_INVALID_REQUEST, message, HttpStatus.BAD_REQUEST, List.of(field));
  }

  private static WatchboxException invalidFilter(String message, String field) {
    return new WatchboxException(
        WATCHBOX_INVALID_FILTER, message, HttpStatus.BAD_REQUEST, List.of(field));
  }
}
