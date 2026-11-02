// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine;

import java.util.List;
import java.util.UUID;

public class MapperEngineException extends Exception {

  private final String code;
  private final List<UUID> ruleIds;
  private final int recordIndex;

  public MapperEngineException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.ruleIds = List.of();
    this.recordIndex = -1;
  }

  public MapperEngineException(String code, String message, Throwable cause, int recordIndex) {
    super(message, cause);
    this.code = code;
    this.ruleIds = List.of();
    this.recordIndex = recordIndex;
  }

  public MapperEngineException(
      String code, String message, UUID ruleId, Throwable cause, int recordIndex) {
    super(message, cause);
    this.code = code;
    this.ruleIds = List.of(ruleId);
    this.recordIndex = recordIndex;
  }

  public MapperEngineException(
      String code, String message, List<UUID> ruleIds, Throwable cause, int recordIndex) {
    super(message, cause);
    this.code = code;
    this.ruleIds = ruleIds;
    this.recordIndex = recordIndex;
  }

  public String getCode() {
    return code;
  }

  public List<UUID> getRuleIds() {
    return ruleIds;
  }

  public int getRecordIndex() {
    return recordIndex;
  }
}
