// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.extension;

import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionHandler;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionRequest;
import java.util.List;
import java.util.Map;

/**
 * The operation answering a request, with the variables its path captured.
 *
 * @param pathVariables each variable of the operation's path, decoded
 */
public record OperationMatch(
    ExtensionOperation<?, ?, ?> operation, Map<String, String> pathVariables) {

  public OperationMatch {
    pathVariables = Map.copyOf(pathVariables);
  }

  /**
   * Invokes the operation's handler.
   *
   * <p>The registry has checked that the extension reads the settings type its schema resolves to,
   * and the caller reads the body into the operation's declared request type, so the handler's type
   * parameters hold.
   *
   * @param body the request body's value, read as the operation's request type, or null for none
   * @return the response body's value, or null for none
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Object handle(
      ConnectionExtensionContext<?> context, Map<String, List<String>> query, Object body)
      throws ConnectionExtensionException {
    var handler = (ExtensionHandler) operation.handler();
    return handler.handle(context, new ExtensionRequest<>(pathVariables, query, body));
  }
}
