// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

public interface RealTimeEventHandler<I> {

  void process(I input);
}
