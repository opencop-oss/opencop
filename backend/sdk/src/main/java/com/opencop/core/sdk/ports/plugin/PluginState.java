// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin;

public enum PluginState {
  READY("Ready"),
  RUNNING("Running"),
  STOPPED("Stopped"),
  FAILED_TO_START("Failed to Start");

  private final String state;

  PluginState(String state) {
    this.state = state;
  }

  public String description() {
    return state;
  }
}
