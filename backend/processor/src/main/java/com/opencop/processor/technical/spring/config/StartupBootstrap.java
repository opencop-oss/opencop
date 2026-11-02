// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Installs Processor's bundled mappers before dependent lifecycle components start. */
@Component
public class StartupBootstrap implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(StartupBootstrap.class);

  private final BundledProfileInstaller installer;

  private volatile boolean running;

  public StartupBootstrap(BundledProfileInstaller installer) {
    this.installer = installer;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;

    log.info("Installing bundled mappers");
    installer.installMappers();
  }

  @Override
  public synchronized void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** Runs before dependent lifecycle components such as Kafka listeners. */
  @Override
  public int getPhase() {
    return Integer.MIN_VALUE;
  }
}
