// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

public final class SessionLifetimeExpiredException extends RuntimeException {

  public SessionLifetimeExpiredException() {
    super("The absolute authentication lifetime has ended");
  }
}
