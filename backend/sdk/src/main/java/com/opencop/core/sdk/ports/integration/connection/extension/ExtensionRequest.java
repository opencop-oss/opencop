// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the caller sent to an extension operation, read by the host.
 *
 * @param pathVariables each variable of the operation's path, decoded
 * @param query every query parameter with its values, decoded as form data: a {@code +} is a space,
 *     and {@code %2B} a plus
 * @param body the request body's value, or null when the operation takes none
 * @param <B> the type of the request body's value
 */
public record ExtensionRequest<B>(
    Map<String, String> pathVariables, Map<String, List<String>> query, B body) {

  public ExtensionRequest {
    pathVariables = Map.copyOf(Objects.requireNonNull(pathVariables, "pathVariables"));
    query = Map.copyOf(Objects.requireNonNull(query, "query"));
  }

  /**
   * @throws IllegalArgumentException when the operation's path declares no such variable
   */
  public String pathVariable(String name) {
    var value = pathVariables.get(name);
    if (value == null) {
      throw new IllegalArgumentException("The operation's path declares no variable " + name);
    }
    return value;
  }
}
