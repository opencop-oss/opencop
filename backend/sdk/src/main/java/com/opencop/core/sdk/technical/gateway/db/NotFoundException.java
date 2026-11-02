// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

public class NotFoundException extends PersistenceException {

  public NotFoundException(String message) {
    super(message);
  }
}
