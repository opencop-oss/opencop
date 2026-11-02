// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin.api;

public interface ChainOfResponsibilityHandler<I, O> {

  O process(I event, O output);
}
