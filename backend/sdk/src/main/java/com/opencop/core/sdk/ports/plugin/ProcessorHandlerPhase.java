// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin;

public enum ProcessorHandlerPhase {
  TRANSFORM("Transform"),
  VERIFY("Verify"),
  NORMALIZE("Normalize"),
  HYDRATE("Hydrate"),
  UPDATE("Update");

  private final String phase;

  ProcessorHandlerPhase(String phase) {
    this.phase = phase;
  }

  public String phase() {
    return phase;
  }
}
