// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

/** A request body not yet read, because the type it binds to depends on the operation. */
public interface ExtensionRequestBody {

  /**
   * @throws UnreadableExtensionBodyException when the body is missing or is not a valid value of
   *     the type
   */
  <T> T read(Class<T> type);
}
