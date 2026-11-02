// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.service.DataSourceAutoStartService;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-starts enabled data sources on Core startup.
 *
 * <p>Core owns the DB and orchestration. On startup, it reads all enabled data sources and tells
 * the appropriate Connect instance to start each one.
 *
 * <p><b>Multi-instance safety:</b> Only the startup leader (determined by {@link StartupLeader} via
 * Redis SETNX) runs auto-start. Before starting, the leader polls for expected Connect instances to
 * ensure route-fresh workers are visible before desired-state nudges are published. Fail-open: if
 * the timeout expires, proceeds with whatever instances are available.
 */
@Configuration
@EnableConfigurationProperties(CoreProperties.class)
public class DataSourceAutoStartConfig {

  private static final Logger log = LoggerFactory.getLogger(DataSourceAutoStartConfig.class);

  @Bean
  DataSourceStartupCoordinator dataSourceStartupCoordinator() {
    return new DataSourceStartupCoordinator();
  }

  @Bean
  public ApplicationRunner enabledDataSourcesAutoStarter(
      DataSourceAutoStartService autoStartService,
      CoreProperties coreProperties,
      StartupLeader startupLeader,
      ConnectInstanceResolver connectInstanceResolver,
      DataSourceStartupCoordinator startupCoordinator) {
    return _ -> {
      if (!coreProperties.getAutoStartEnabledDataSourcesOnStartup().isEnabled()) {
        log.info("Core startup: auto-start enabled data sources disabled, skipping");
        startupCoordinator.autoStartSkipped();
        return;
      }

      if (!startupLeader.isLeader()) {
        log.info("Core startup: not startup leader, skipping auto-start");
        startupCoordinator.autoStartSkipped();
        return;
      }

      startupCoordinator.autoStartStarted();

      // Run in background so the ApplicationRunner completes immediately.
      // Blocking here prevents ApplicationReadyEvent from firing, which keeps
      // /actuator/health returning 503 — deadlocking services that wait for Core.
      Thread.startVirtualThread(
          () -> {
            try {
              var autoStart = coreProperties.getAutoStartEnabledDataSourcesOnStartup();
              int expected = autoStart.getExpectedConnectInstances();
              int timeoutSec = autoStart.getConnectReadyTimeoutSeconds();

              connectInstanceResolver.clearInstanceRegistry();

              Instant start = Instant.now();
              List<ConnectInstanceInfo> routableInstances =
                  connectInstanceResolver.getRoutableInstances(
                      Duration.ofSeconds(timeoutSec), expected);
              int found = routableInstances.size();
              long waitedSec = Duration.between(start, Instant.now()).toSeconds();
              if (found < expected) {
                log.warn(
                    "Timed out waiting for Connect instances ({}/{}) after {}s, proceeding with"
                        + " available",
                    found,
                    expected,
                    waitedSec);
              } else {
                log.info(
                    "All Connect instances ready ({}/{}) after {}s", found, expected, waitedSec);
              }

              log.info("Core startup: auto-starting enabled data sources...");
              autoStartService.autoStartEnabledDataSources(routableInstances);
              startupCoordinator.autoStartCompleted();
            } catch (Exception e) {
              startupCoordinator.autoStartFailed();
              log.error("Data source auto-start failed", e);
            }
          });
    };
  }
}
