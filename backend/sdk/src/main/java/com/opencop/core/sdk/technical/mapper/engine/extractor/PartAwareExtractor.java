// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.extractor;

import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;

public interface PartAwareExtractor<R> {

  /** How many parts exist for this record (>= 1). */
  int partCount(R node);

  /**
   * Select a string for a specific part index. Default implementation can fall back to non-part
   * behavior.
   */
  String selectString(R node, int partIndex, MappingRule rule) throws MapperEngineException;
}
