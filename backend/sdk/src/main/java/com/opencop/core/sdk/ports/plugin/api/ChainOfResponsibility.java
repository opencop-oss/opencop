// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin.api;

public interface ChainOfResponsibility<I, O> {

  void handle(I event, O output);
}
