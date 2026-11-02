// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin.annotation.processor;

import com.opencop.core.sdk.ports.plugin.ProcessorHandlerPhase;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Service;

@Service
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProcessorHandler {

  ProcessorHandlerPhase phase();

  // Lower is more important (1 would process before 2)
  int priority();
}
