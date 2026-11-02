// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpencopBrokerConsumerException extends RuntimeException {

  private static final Logger log = LoggerFactory.getLogger(OpencopBrokerConsumerException.class);

  public OpencopBrokerConsumerException(String message, Throwable throwable) {
    super(message, throwable);
    log.error(message);
  }
}
