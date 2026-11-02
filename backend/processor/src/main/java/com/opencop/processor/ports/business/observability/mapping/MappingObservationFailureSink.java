// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.observability.mapping;

public interface MappingObservationFailureSink {

  void report(Class<?> exceptionClass);
}
