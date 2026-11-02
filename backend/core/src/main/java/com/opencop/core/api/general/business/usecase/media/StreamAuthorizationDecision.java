// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

public record StreamAuthorizationDecision(boolean allowed, StreamAuthorizationReason reason) {

  public static StreamAuthorizationDecision allow() {
    return new StreamAuthorizationDecision(true, StreamAuthorizationReason.ALLOWED);
  }

  public static StreamAuthorizationDecision deny(StreamAuthorizationReason reason) {
    return new StreamAuthorizationDecision(false, reason);
  }
}
