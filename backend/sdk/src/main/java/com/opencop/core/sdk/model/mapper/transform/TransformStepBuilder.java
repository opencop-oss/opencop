// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.transform;

import static java.util.Objects.requireNonNull;

import java.util.Map;

public final class TransformStepBuilder {

  private final TransformOperation operation;
  private String pattern;
  private String replacement;
  private String format;
  private String value;
  private Map<String, String> mapValues;
  private String mapDefault;

  private TransformStepBuilder(TransformOperation operation) {
    this.operation = requireNonNull(operation);
  }

  public static TransformStepBuilder op(TransformOperation op) {
    return new TransformStepBuilder(op);
  }

  public TransformStepBuilder pattern(String pattern) {
    this.pattern = pattern;
    return this;
  }

  public TransformStepBuilder replacement(String replacement) {
    this.replacement = replacement;
    return this;
  }

  public TransformStepBuilder format(String format) {
    this.format = format;
    return this;
  }

  public TransformStepBuilder value(String value) {
    this.value = value;
    return this;
  }

  public TransformStepBuilder mapValues(Map<String, String> mapValues) {
    this.mapValues = (mapValues == null) ? null : Map.copyOf(mapValues);
    return this;
  }

  public TransformStepBuilder mapDefault(String mapDefault) {
    this.mapDefault = mapDefault;
    return this;
  }

  public TransformStep build() {
    return new TransformStep(operation, pattern, replacement, format, value, mapValues, mapDefault);
  }

  public static TransformStep trim() {
    return op(TransformOperation.TRIM).build();
  }

  public static TransformStep upper() {
    return op(TransformOperation.UPPER).build();
  }

  public static TransformStep lower() {
    return op(TransformOperation.LOWER).build();
  }

  public static TransformStep parseDateTime() {
    return op(TransformOperation.PARSE_DATE_TIME).build();
  }

  public static TransformStep radToDegrees() {
    return op(TransformOperation.RAD_TO_DEGREES).build();
  }

  public static TransformStep gradToDegrees() {
    return op(TransformOperation.GRAD_TO_DEGREES).build();
  }

  public static TransformStep milsToDegrees() {
    return op(TransformOperation.MILS_TO_DEGREES).build();
  }

  public static TransformStep utmToPoint() {
    return op(TransformOperation.UTM_TO_POINT).build();
  }

  public static TransformStep usngToPoint() {
    return op(TransformOperation.USNG_TO_POINT).build();
  }

  public static TransformStep dmsToPoint() {
    return op(TransformOperation.DMS_TO_POINT).build();
  }

  public static TransformStep dmToPoint() {
    return op(TransformOperation.DM_TO_POINT).build();
  }

  public static TransformStep geohashToPoint() {
    return op(TransformOperation.GEOHASH_TO_POINT).build();
  }

  public static TransformStep kphToMps() {
    return op(TransformOperation.KPH_TO_MPS).build();
  }

  public static TransformStep mphToMps() {
    return op(TransformOperation.MPH_TO_MPS).build();
  }

  public static TransformStep knotsToMps() {
    return op(TransformOperation.KNOTS_TO_MPS).build();
  }

  public static TransformStep fpsToMps() {
    return op(TransformOperation.FPS_TO_MPS).build();
  }

  public static TransformStep defaultNow() {
    return op(TransformOperation.DEFAULT_NOW).build();
  }

  public static TransformStep addDuration(String value) {
    return op(TransformOperation.ADD_DURATION).value(value).build();
  }

  public static TransformStep replaceRegex(String pattern, String replacement) {
    return op(TransformOperation.REPLACE_REGEX).pattern(pattern).replacement(replacement).build();
  }

  public static TransformStep defaultIfBlank(String value) {
    return op(TransformOperation.DEFAULT_IF_BLANK).value(value).build();
  }
}
