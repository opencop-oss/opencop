// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

public class ScimUserMutationConflictException extends RuntimeException {

  public ScimUserMutationConflictException(String detail, Throwable cause) {
    super(detail, cause);
  }
}
