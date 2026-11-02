// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BoundedMetricTagGuard {

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[\\da-fA-F]{8}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{12}$");
  private static final Pattern COORDINATE_PATTERN =
      Pattern.compile("^[+-]?\\d{1,3}(?:\\.\\d+)?(?:,[+-]?\\d{1,3}(?:\\.\\d+)?)?$");

  private BoundedMetricTagGuard() {}

  public static String requireAllowed(String tagKey, String value, Set<String> allowedValues) {
    if (tagKey == null || tagKey.isBlank()) {
      throw new IllegalArgumentException("tag key must be non-empty");
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(tagKey + " must be non-empty");
    }
    if (UUID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(tagKey + " must not be a UUID-like value");
    }
    if (COORDINATE_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(tagKey + " must not be a coordinate-like value");
    }
    if (allowedValues == null || !allowedValues.contains(value)) {
      throw new IllegalArgumentException(tagKey + " must be one of " + allowedValues);
    }
    return value;
  }

  public static Set<String> allowedValues(BoundedMetricTagValue... values) {
    return Arrays.stream(values)
        .map(BoundedMetricTagValue::value)
        .collect(Collectors.toUnmodifiableSet());
  }
}
