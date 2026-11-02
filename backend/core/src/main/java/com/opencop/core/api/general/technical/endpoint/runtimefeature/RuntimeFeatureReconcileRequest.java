// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The set of names a client's build declares.
 *
 * <p>Names-only, for the same reason registration is name-only: consumer class, audience, and
 * revision are server-owned, so there is nothing else a caller could usefully send. Unsupported
 * fields are ignored rather than rejected — they cannot reach the registered state.
 */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class RuntimeFeatureReconcileRequest {

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description =
          "Wire names this build declares. Duplicates are collapsed. At most 200 per call.",
      example = "[\"themes-page\", \"plugin-loader\"]")
  private List<String> names;

  public List<String> getNames() {
    return names;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }
}
