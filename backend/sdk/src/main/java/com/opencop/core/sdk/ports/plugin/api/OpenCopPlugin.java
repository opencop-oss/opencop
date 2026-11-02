// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin.api;

import java.util.UUID;

public interface OpenCopPlugin<I, O> {

  UUID getUuid();

  String getName();

  String getDescription();

  String getVersion();

  void initialize(
      UUID uuid,
      String version,
      String name,
      String description,
      ChainOfResponsibility<I, O> chain);

  void start();

  void stop();
}
