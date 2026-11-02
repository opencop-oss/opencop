// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.api.watchbox.business.model.WatchboxDetectionFilter;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionPageRequest;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.usecase.ReadWatchboxDetections;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Watchboxes")
public class WatchboxDetectionsEndpoint {
  private static final String KIND_VALUES =
      Arrays.stream(WatchboxTransitionKind.values())
          .map(Enum::name)
          .collect(Collectors.joining(", "));
  private final ReadWatchboxDetections usecase;

  public WatchboxDetectionsEndpoint(ReadWatchboxDetections usecase) {
    this.usecase = usecase;
  }

  @GetMapping("/v1/watchbox-detections")
  @Operation(
      summary = "List recent Watchbox detections visible to the current user",
      description =
          """
      Newest first; sort is not supported. Default size 25; oversized size is clamped to 2000,
      size below 1 uses the default, and negative page becomes 0. kind may repeat; blank kind values are ignored.
      objectDisplayName is nullable and at most 255 characters.
      A caller sees a detection if and only if they can fully see its Watchbox.
      Earlier tracking generations are included and carry configEpoch. Rows are gone after the
      retention window (72 hours by default, measured on the event time). Page and total are not a
      stable snapshot.
      """)
  @ApiResponse(
      responseCode = "200",
      content = @Content(examples = @ExampleObject(value = WatchboxOpenApiExamples.DETECTION_PAGE)))
  @ApiResponse(
      responseCode = "400",
      content =
          @Content(examples = @ExampleObject(value = WatchboxOpenApiExamples.INVALID_REQUEST)))
  @ApiResponse(
      responseCode = "404",
      content = @Content(examples = @ExampleObject(value = WatchboxOpenApiExamples.NOT_FOUND)))
  // Documented as plain page/size: the generated Pageable object would advertise a sort field
  // this endpoint rejects, so its default example could never succeed.
  @Parameters({
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        description = "Zero-based page index; a negative value becomes 0",
        schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        description = "Rows per page; below 1 uses the default, above 2000 is clamped to 2000",
        schema = @Schema(type = "integer", minimum = "1", maximum = "2000", defaultValue = "25"))
  })
  public ResponseEntity<PageResponse<WatchboxDetectionResponse>> list(
      @RequestParam(required = false) UUID watchboxId,
      @Parameter(
              description = "Transition kinds to include; repeatable; blank values are ignored",
              array = @ArraySchema(schema = @Schema(implementation = WatchboxTransitionKind.class)))
          @RequestParam(name = "kind", required = false)
          List<String> kinds,
      @Parameter(hidden = true) @PageableDefault(size = 25) Pageable pageable,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    if (pageable.getSort().isSorted()) {
      throw invalid("Detections are newest-first; sort is not supported", "sort");
    }
    var page =
        usecase.list(
            new WatchboxDetectionFilter(watchboxId, parseKinds(kinds)),
            new WatchboxDetectionPageRequest(pageable.getPageNumber(), pageable.getPageSize()),
            caller(authentication));
    return ResponseEntity.ok(
        new PageResponse<>(
            page.content().stream().map(WatchboxDetectionResponse::from).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()));
  }

  @GetMapping("/v1/watchbox-detections/{detectionId}")
  @Operation(
      summary = "Get one Watchbox detection with its related events",
      description =
          """
      A caller sees a detection if and only if they can fully see its Watchbox. Earlier tracking
      generations are included and carry configEpoch. Rows are gone after the retention window
      (72 hours by default, measured on the event time). Embeds are the stored triggering revision
      as currently held and the object's latest event, each null when absent, unreadable, or not
      visible.
      """)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(examples = @ExampleObject(value = WatchboxOpenApiExamples.DETECTION_DETAIL)))
  @ApiResponse(
      responseCode = "404",
      content = @Content(examples = @ExampleObject(value = WatchboxOpenApiExamples.NOT_FOUND)))
  public ResponseEntity<WatchboxDetectionDetailResponse> get(
      @PathVariable UUID detectionId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(
        WatchboxDetectionDetailResponse.from(usecase.get(detectionId, caller(authentication))));
  }

  /** Blank values are the no-filter idiom; anything else must name a transition kind exactly. */
  private static EnumSet<WatchboxTransitionKind> parseKinds(List<String> kinds) {
    var selected = EnumSet.noneOf(WatchboxTransitionKind.class);
    if (kinds == null) {
      return selected;
    }
    for (var kind : kinds) {
      if (kind.isBlank()) {
        continue;
      }
      try {
        selected.add(WatchboxTransitionKind.valueOf(kind));
      } catch (IllegalArgumentException _) {
        throw invalid("kind must be one of " + KIND_VALUES, "kind");
      }
    }
    return selected;
  }

  private static WatchboxException invalid(String message, String field) {
    return new WatchboxException(
        WatchboxErrorCode.WATCHBOX_INVALID_REQUEST,
        message,
        HttpStatus.BAD_REQUEST,
        List.of(field));
  }

  private static WatchboxCaller caller(OpenCopPrincipal authentication) {
    return new WatchboxCaller(
        authentication.internalUserId(),
        authentication.isGlobalAdmin(),
        authentication.fullName(),
        authentication.email());
  }
}
