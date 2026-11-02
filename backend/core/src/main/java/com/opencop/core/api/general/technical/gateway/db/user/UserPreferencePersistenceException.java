// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;

public abstract class UserPreferencePersistenceException extends PersistenceException {

  protected UserPreferencePersistenceException(String message) {
    super(message);
  }

  public static class NotFound extends UserPreferencePersistenceException {
    public NotFound(String message) {
      super(message);
    }
  }

  public static class BadRequest extends UserPreferencePersistenceException {
    public BadRequest(String message) {
      super(message);
    }
  }

  public static class Conflict extends UserPreferencePersistenceException {
    public Conflict(String message) {
      super(message);
    }
  }
}
