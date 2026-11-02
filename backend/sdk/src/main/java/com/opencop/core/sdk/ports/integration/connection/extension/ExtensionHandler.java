// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

/**
 * Serves one extension operation.
 *
 * <p>A handler reaching a remote system sets connect and read timeouts on its own client and
 * reports an expired one as {@link ExtensionFailure#UPSTREAM_TIMEOUT}; the host never interrupts a
 * handler.
 *
 * @param <S> the effective settings type of the connection's schema
 * @param <B> the type of the request body's value, {@link Void} when it takes none
 * @param <R> the type of the response body's value, {@link Void} when it returns none
 */
@FunctionalInterface
public interface ExtensionHandler<S, B, R> {

  /**
   * @return the response body's value, or null when the operation returns none
   * @throws ConnectionExtensionException when the operation fails in a way the caller should see
   */
  R handle(ConnectionExtensionContext<S> context, ExtensionRequest<B> request)
      throws ConnectionExtensionException;
}
