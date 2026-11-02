// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionPathTemplate;
import java.util.Objects;

/**
 * One HTTP operation a connection extension declares, served under the extension on each connection
 * of its schema.
 *
 * @param path the operation's path relative to its extension
 * @param access what the caller must be allowed on the connection
 * @param <S> the effective settings type of the connection's schema
 * @param <B> the type of the request body's value
 * @param <R> the type of the response body's value
 */
public record ExtensionOperation<S, B, R>(
    ExtensionMethod method,
    ExtensionPathTemplate path,
    AccessLevel access,
    ExtensionBody<B> request,
    ExtensionBody<R> response,
    ExtensionHandler<S, B, R> handler) {

  public ExtensionOperation {
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(response, "response");
    Objects.requireNonNull(handler, "handler");
  }

  /**
   * @param path the operation's path relative to its extension, such as {@code
   *     /subscriptions/{name}}
   * @throws IllegalArgumentException when the path is not a valid template
   */
  public static <S, B, R> ExtensionOperation<S, B, R> of(
      ExtensionMethod method,
      String path,
      AccessLevel access,
      ExtensionBody<B> request,
      ExtensionBody<R> response,
      ExtensionHandler<S, B, R> handler) {
    return new ExtensionOperation<>(
        method, ExtensionPathTemplate.parse(path), access, request, response, handler);
  }
}
