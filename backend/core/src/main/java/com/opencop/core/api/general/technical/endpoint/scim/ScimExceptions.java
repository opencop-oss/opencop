// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.scim;

public class ScimExceptions {

  private ScimExceptions() {}

  public static class ScimBadRequestException extends RuntimeException {
    public ScimBadRequestException(String msg) {
      super(msg);
    }
  }

  public static class ScimInvalidFilterException extends RuntimeException {
    public ScimInvalidFilterException(String msg) {
      super("Invalid filter: " + msg);
    }
  }

  public static class ScimNotFoundException extends RuntimeException {
    public ScimNotFoundException(String msg) {
      super(msg);
    }
  }

  public static class ScimConflictException extends RuntimeException {
    public ScimConflictException(String msg) {
      super(msg);
    }
  }
}
