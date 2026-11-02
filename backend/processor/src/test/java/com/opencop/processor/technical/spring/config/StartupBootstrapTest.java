// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class StartupBootstrapTest {

  @Test
  void installsBundledMappersAtStartup() {
    var installer = mock(BundledProfileInstaller.class);

    var bootstrap = new StartupBootstrap(installer);
    bootstrap.start();

    verify(installer).installMappers();
    assertThat(bootstrap.isRunning()).isTrue();
  }
}
