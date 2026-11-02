// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import java.util.Objects;
import java.util.Optional;

/**
 * Checked extractor failure carrying a host category, an optional provider-owned detail code, and
 * optionally the declared field it rejects.
 */
public final class ConnectionInputException extends Exception {

  private final ConnectionInputFailure failure;
  private final String providerDetailCode;
  private final String field;

  public ConnectionInputException(
      ConnectionInputFailure failure, String providerDetailCode, String safeMessage) {
    this(failure, providerDetailCode, safeMessage, null);
  }

  public ConnectionInputException(
      ConnectionInputFailure failure,
      String providerDetailCode,
      String safeMessage,
      Throwable cause) {
    this(failure, providerDetailCode, safeMessage, null, cause);
  }

  /**
   * Rejects the input supplied for one declared field, so a client can point at it.
   *
   * @param field the declared field the rejection concerns
   */
  public static ConnectionInputException rejecting(
      String field, ConnectionInputFailure failure, String providerDetailCode, String safeMessage) {
    Objects.requireNonNull(field, "field");
    return new ConnectionInputException(failure, providerDetailCode, safeMessage, field, null);
  }

  private ConnectionInputException(
      ConnectionInputFailure failure,
      String providerDetailCode,
      String safeMessage,
      String field,
      Throwable cause) {
    Objects.requireNonNull(failure, "failure");
    if (providerDetailCode != null && providerDetailCode.isBlank()) {
      throw new IllegalArgumentException("providerDetailCode must not be blank");
    }
    if (field != null && field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank");
    }
    super(requireSafeMessage(safeMessage), cause);
    this.failure = failure;
    this.providerDetailCode = providerDetailCode;
    this.field = field;
  }

  public ConnectionInputFailure failure() {
    return failure;
  }

  public Optional<String> providerDetailCode() {
    return Optional.ofNullable(providerDetailCode);
  }

  /** Returns the declared field the rejection concerns, if it concerns one. */
  public Optional<String> field() {
    return Optional.ofNullable(field);
  }

  private static String requireSafeMessage(String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("safeMessage must not be blank");
    }
    return message;
  }
}
