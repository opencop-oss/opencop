// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.springframework.stereotype.Component;

@Component
public class ProtocolErrorMessageFactory {

  public SocketMessage create(ClientMessageValidator.Rejection rejection) {
    return UserErrorFrames.errorMessage(
        rejection.errorCode().name(),
        rejection.operation().name(),
        rejection.rejectedDestination(),
        rejection.errorCode().reason());
  }
}
