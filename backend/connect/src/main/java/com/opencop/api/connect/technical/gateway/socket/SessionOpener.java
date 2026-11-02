// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

@FunctionalInterface
public interface SessionOpener {
  Session open() throws Exception;
}
