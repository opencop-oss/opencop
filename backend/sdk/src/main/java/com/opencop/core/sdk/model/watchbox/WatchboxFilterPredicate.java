// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One Watchbox event-property predicate. Offset is 0-based and used only by SUBSTRING_IN.
 * caseInsensitive applies to EQUALS, IN, and SUBSTRING_IN comparisons and is ignored by EXISTS.
 */
public record WatchboxFilterPredicate(
    String path,
    WatchboxFilterOperation operation,
    List<String> values,
    boolean negate,
    Integer offset,
    Integer length,
    boolean caseInsensitive) {

  public WatchboxFilterPredicate {
    values = values == null ? List.of() : List.copyOf(values);
  }

  @JsonCreator
  public static WatchboxFilterPredicate fromJson(
      @JsonProperty("path") String path,
      @JsonProperty("operation") WatchboxFilterOperation operation,
      @JsonProperty("values") List<String> values,
      @JsonProperty("negate") Boolean negate,
      @JsonProperty("offset") Integer offset,
      @JsonProperty("length") Integer length,
      @JsonProperty("caseInsensitive") Boolean caseInsensitive) {
    return new WatchboxFilterPredicate(
        path,
        operation,
        values,
        Boolean.TRUE.equals(negate),
        offset,
        length,
        Boolean.TRUE.equals(caseInsensitive));
  }

  public WatchboxFilterPredicate(
      String path, WatchboxFilterOperation operation, List<String> values) {
    this(path, operation, values, false, null, null, false);
  }
}
