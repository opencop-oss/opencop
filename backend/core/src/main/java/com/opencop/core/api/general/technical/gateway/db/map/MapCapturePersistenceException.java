// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;

public abstract class MapCapturePersistenceException extends PersistenceException {

  protected MapCapturePersistenceException(String message) {
    super(message);
  }

  public static class NotFound extends MapCapturePersistenceException {
    public NotFound(String message) {
      super(message);
    }
  }

  public static class BadRequest extends MapCapturePersistenceException {
    public BadRequest(String message) {
      super(message);
    }
  }

  public static class Conflict extends MapCapturePersistenceException {
    public Conflict(String message) {
      super(message);
    }
  }
}
