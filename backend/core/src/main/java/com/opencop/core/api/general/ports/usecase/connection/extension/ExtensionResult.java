// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

/**
 * What an extension operation answered.
 *
 * @param body the response body's value, null when the operation returns none
 * @param hasBody whether the operation declares a response body, which decides between 200 and 204
 */
public record ExtensionResult(Object body, boolean hasBody) {

  public static ExtensionResult none() {
    return new ExtensionResult(null, false);
  }

  public static ExtensionResult of(Object body) {
    return new ExtensionResult(body, true);
  }
}
