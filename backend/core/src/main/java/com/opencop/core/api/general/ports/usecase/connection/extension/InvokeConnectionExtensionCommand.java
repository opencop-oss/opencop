// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One request to a connection extension.
 *
 * @param path the request path below the extension, as decoded segments
 * @param query every query parameter with its values, decoded
 * @param body the request body, read only once the operation's body type is known
 */
public record InvokeConnectionExtensionCommand(
    UUID sourceId,
    UUID userId,
    boolean isGlobalAdmin,
    String extensionId,
    ExtensionMethod method,
    List<String> path,
    Map<String, List<String>> query,
    ExtensionRequestBody body) {

  public InvokeConnectionExtensionCommand {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(extensionId, "extensionId");
    Objects.requireNonNull(method, "method");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    query = Map.copyOf(Objects.requireNonNull(query, "query"));
    Objects.requireNonNull(body, "body");
  }
}
