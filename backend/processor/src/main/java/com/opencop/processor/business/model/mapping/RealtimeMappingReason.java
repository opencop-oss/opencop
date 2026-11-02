// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum RealtimeMappingReason implements BoundedMetricTagValue {
  NONE("none"),
  PROFILE_DISABLED("profile_disabled"),
  ON_MISSING_ERROR("on_missing_error"),
  PAYLOAD_ERROR("payload_error"),
  UNSUPPORTED_MAPPER_FORMAT("unsupported_mapper_format"),
  ENGINE_EXCEPTION("engine_exception"),
  OTHER_MAPPING_ERROR("other_mapping_error"),
  MULTIPLE_MAPPING_ERRORS("multiple_mapping_errors"),
  EXPLICIT_DECODER_CONTRACT("explicit_decoder_contract"),
  ACCEPTED_DECODER_WARNING("accepted_decoder_warning"),
  DECODER_DIAGNOSTIC("decoder_diagnostic"),
  DECODER_NO_OUTPUT("decoder_no_output"),
  THROWN("thrown");

  private final String value;

  RealtimeMappingReason(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
