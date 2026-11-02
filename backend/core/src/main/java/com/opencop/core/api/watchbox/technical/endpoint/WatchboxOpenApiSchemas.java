// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;

final class WatchboxOpenApiSchemas {

  private WatchboxOpenApiSchemas() {}

  @Schema(name = "WatchboxCreateRequest")
  record CreateRequest(
      @Schema(maxLength = WatchboxConfigurationLimits.MAX_NAME_CODE_POINTS) String name,
      @Schema(maxLength = WatchboxConfigurationLimits.MAX_DESCRIPTION_CODE_POINTS)
          String description,
      Polygon polygon,
      WatchboxDirection direction,
      @ArraySchema(
              minItems = 1,
              maxItems = WatchboxConfigurationLimits.MAX_LAYER_IDS,
              uniqueItems = true,
              schema = @Schema(type = "string", format = "uuid"))
          Set<UUID> layerIds,
      WatchboxFilter filter,
      Boolean enabled) {}

  @Schema(name = "WatchboxPatchRequest")
  record PatchRequest(
      @Schema(maxLength = WatchboxConfigurationLimits.MAX_NAME_CODE_POINTS) String name,
      @Schema(maxLength = WatchboxConfigurationLimits.MAX_DESCRIPTION_CODE_POINTS)
          String description,
      Polygon polygon,
      WatchboxDirection direction,
      @ArraySchema(
              minItems = 1,
              maxItems = WatchboxConfigurationLimits.MAX_LAYER_IDS,
              uniqueItems = true,
              schema = @Schema(type = "string", format = "uuid"))
          Set<UUID> layerIds,
      WatchboxFilter filter,
      Boolean enabled,
      Boolean confirmTrackingReset) {}
}
