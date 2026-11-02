// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checked extension operation failure carrying a host failure family, and optionally the
 * extension's own error code, which the host answers with in place of the family's default.
 *
 * <p>The message is shown to the caller, so it must never contain remote URLs, host names,
 * credentials or other material the caller should not see.
 *
 * <p>Codes starting {@code CONNECTION_}, {@code EXTENSION_} or {@code INTEGRATION_} are the host's,
 * so a client never mistakes an extension's answer for the host's. The only ones an extension may
 * use are those the {@link ConnectionRecordStore} raises, to pass such a failure on.
 */
public final class ConnectionExtensionException extends Exception {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");
  private static final List<String> HOST_PREFIXES =
      List.of("CONNECTION_", "EXTENSION_", "INTEGRATION_");
  private static final Set<String> PASSED_ON_HOST_CODES =
      Set.of(ConnectionRecordStore.REVISION_CONFLICT, ConnectionRecordStore.LIMIT_EXCEEDED);

  private final ExtensionFailure failure;
  private final String code;

  public ConnectionExtensionException(ExtensionFailure failure, String safeMessage) {
    this(failure, null, safeMessage, null);
  }

  /**
   * @param code the extension's own error code, in upper snake case and outside the host's
   *     prefixes, or null for the family's default
   */
  public ConnectionExtensionException(ExtensionFailure failure, String code, String safeMessage) {
    this(failure, code, safeMessage, null);
  }

  public ConnectionExtensionException(
      ExtensionFailure failure, String code, String safeMessage, Throwable cause) {
    super(requireSafeMessage(safeMessage), cause);
    this.failure = Objects.requireNonNull(failure, "failure");
    this.code = requireCodeSyntax(code);
  }

  public ExtensionFailure failure() {
    return failure;
  }

  /** The extension's own error code, when it gave one. */
  public Optional<String> code() {
    return Optional.ofNullable(code);
  }

  private static String requireSafeMessage(String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("safeMessage must not be blank");
    }
    return message;
  }

  private static String requireCodeSyntax(String code) {
    if (code != null && !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("code must be upper snake case: " + code);
    }
    if (code != null && isHosts(code) && !PASSED_ON_HOST_CODES.contains(code)) {
      throw new IllegalArgumentException("code is reserved for the host: " + code);
    }
    return code;
  }

  private static boolean isHosts(String code) {
    return HOST_PREFIXES.stream().anyMatch(code::startsWith);
  }
}
