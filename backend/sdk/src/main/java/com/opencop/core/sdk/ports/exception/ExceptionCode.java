// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.exception;

import org.springframework.http.HttpStatus;

public interface ExceptionCode {

  String code();

  String description();

  HttpStatus httpStatus();

  default String format(Object... args) {
    return description().formatted(args);
  }
}
