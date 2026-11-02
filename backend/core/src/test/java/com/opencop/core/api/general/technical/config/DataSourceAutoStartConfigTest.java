// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.service.DataSourceAutoStartService;
import com.opencop.core.api.general.technical.config.DataSourceStartupCoordinator.AutoStartStatus;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class DataSourceAutoStartConfigTest {

  @Test
  void enabledDataSourcesAutoStarter_whenLeader_clearsInstanceRegistryBeforeAutoStart()
      throws Exception {
    var autoStartService = mock(DataSourceAutoStartService.class);
    var startupLeader = mock(StartupLeader.class);
    var connectInstanceResolver = mock(ConnectInstanceResolver.class);
    var coreProperties = new CoreProperties();
    var startupCoordinator = new DataSourceStartupCoordinator();
    var autoStart = coreProperties.getAutoStartEnabledDataSourcesOnStartup();
    autoStart.setExpectedConnectInstances(1);
    autoStart.setConnectReadyTimeoutSeconds(1);
    autoStart.setConnectReadyPollIntervalSeconds(1);

    when(startupLeader.isLeader()).thenReturn(true);
    var routableInstances = List.of(connectInfo("connect-new"));
    when(connectInstanceResolver.getRoutableInstances(
            ArgumentMatchers.any(), ArgumentMatchers.eq(1)))
        .thenReturn(routableInstances);

    var runner =
        new DataSourceAutoStartConfig()
            .enabledDataSourcesAutoStarter(
                autoStartService,
                coreProperties,
                startupLeader,
                connectInstanceResolver,
                startupCoordinator);

    runner.run(null);

    verify(connectInstanceResolver, timeout(1_000)).clearInstanceRegistry();
    verify(connectInstanceResolver, timeout(1_000))
        .getRoutableInstances(ArgumentMatchers.any(), ArgumentMatchers.eq(1));
    verify(autoStartService, timeout(1_000)).autoStartEnabledDataSources(routableInstances);
    assertAutoStartStatus(startupCoordinator, AutoStartStatus.COMPLETED);
    var inOrder = inOrder(connectInstanceResolver, autoStartService);
    inOrder.verify(connectInstanceResolver).clearInstanceRegistry();
    inOrder
        .verify(connectInstanceResolver)
        .getRoutableInstances(ArgumentMatchers.any(), ArgumentMatchers.eq(1));
    inOrder.verify(autoStartService).autoStartEnabledDataSources(routableInstances);
    verify(connectInstanceResolver, never())
        .getRoutableInstancesForCurrentRegistry(ArgumentMatchers.any());
  }

  @Test
  void enabledDataSourcesAutoStarter_whenMultipleWorkersExpected_waitsForAllRouteFreshWorkers()
      throws Exception {
    var autoStartService = mock(DataSourceAutoStartService.class);
    var startupLeader = mock(StartupLeader.class);
    var connectInstanceResolver = mock(ConnectInstanceResolver.class);
    var coreProperties = new CoreProperties();
    var startupCoordinator = new DataSourceStartupCoordinator();
    var autoStart = coreProperties.getAutoStartEnabledDataSourcesOnStartup();
    autoStart.setExpectedConnectInstances(2);
    autoStart.setConnectReadyTimeoutSeconds(1);
    autoStart.setConnectReadyPollIntervalSeconds(1);

    var connectA = connectInfo("connect-a");
    var connectB = connectInfo("connect-b");

    when(startupLeader.isLeader()).thenReturn(true);
    when(connectInstanceResolver.getRoutableInstances(
            ArgumentMatchers.any(), ArgumentMatchers.eq(2)))
        .thenReturn(List.of(connectA, connectB));

    var runner =
        new DataSourceAutoStartConfig()
            .enabledDataSourcesAutoStarter(
                autoStartService,
                coreProperties,
                startupLeader,
                connectInstanceResolver,
                startupCoordinator);

    runner.run(null);

    verify(autoStartService, timeout(1_000))
        .autoStartEnabledDataSources(List.of(connectA, connectB));
    assertAutoStartStatus(startupCoordinator, AutoStartStatus.COMPLETED);
    verify(connectInstanceResolver)
        .getRoutableInstances(ArgumentMatchers.any(), ArgumentMatchers.eq(2));
    verify(connectInstanceResolver, never())
        .getRoutableInstancesForCurrentRegistry(ArgumentMatchers.any());
  }

  @Test
  void enabledDataSourcesAutoStarter_whenDisabled_marksAutoStartSkipped() throws Exception {
    var autoStartService = mock(DataSourceAutoStartService.class);
    var startupLeader = mock(StartupLeader.class);
    var connectInstanceResolver = mock(ConnectInstanceResolver.class);
    var coreProperties = new CoreProperties();
    var startupCoordinator = new DataSourceStartupCoordinator();
    coreProperties.getAutoStartEnabledDataSourcesOnStartup().setEnabled(false);

    var runner =
        new DataSourceAutoStartConfig()
            .enabledDataSourcesAutoStarter(
                autoStartService,
                coreProperties,
                startupLeader,
                connectInstanceResolver,
                startupCoordinator);

    runner.run(null);

    assertEquals(AutoStartStatus.SKIPPED, startupCoordinator.autoStartStatus());
    verifyNoInteractions(autoStartService, connectInstanceResolver);
  }

  @Test
  void enabledDataSourcesAutoStarter_whenNotLeader_marksAutoStartSkipped() throws Exception {
    var autoStartService = mock(DataSourceAutoStartService.class);
    var startupLeader = mock(StartupLeader.class);
    var connectInstanceResolver = mock(ConnectInstanceResolver.class);
    var coreProperties = new CoreProperties();
    var startupCoordinator = new DataSourceStartupCoordinator();

    when(startupLeader.isLeader()).thenReturn(false);

    var runner =
        new DataSourceAutoStartConfig()
            .enabledDataSourcesAutoStarter(
                autoStartService,
                coreProperties,
                startupLeader,
                connectInstanceResolver,
                startupCoordinator);

    runner.run(null);

    assertEquals(AutoStartStatus.SKIPPED, startupCoordinator.autoStartStatus());
    verifyNoInteractions(autoStartService, connectInstanceResolver);
  }

  @Test
  void enabledDataSourcesAutoStarter_whenAutoStartFails_marksAutoStartFailed() throws Exception {
    var autoStartService = mock(DataSourceAutoStartService.class);
    var startupLeader = mock(StartupLeader.class);
    var connectInstanceResolver = mock(ConnectInstanceResolver.class);
    var coreProperties = new CoreProperties();
    var startupCoordinator = new DataSourceStartupCoordinator();
    var autoStart = coreProperties.getAutoStartEnabledDataSourcesOnStartup();
    autoStart.setExpectedConnectInstances(1);
    autoStart.setConnectReadyTimeoutSeconds(1);
    autoStart.setConnectReadyPollIntervalSeconds(1);

    when(startupLeader.isLeader()).thenReturn(true);
    var routableInstances = List.of(connectInfo("connect-new"));
    when(connectInstanceResolver.getRoutableInstances(
            ArgumentMatchers.any(), ArgumentMatchers.eq(1)))
        .thenReturn(routableInstances);
    doThrow(new IllegalStateException("boom"))
        .when(autoStartService)
        .autoStartEnabledDataSources(routableInstances);

    var runner =
        new DataSourceAutoStartConfig()
            .enabledDataSourcesAutoStarter(
                autoStartService,
                coreProperties,
                startupLeader,
                connectInstanceResolver,
                startupCoordinator);

    runner.run(null);

    assertAutoStartStatus(startupCoordinator, AutoStartStatus.FAILED);
  }

  private static void assertAutoStartStatus(
      DataSourceStartupCoordinator startupCoordinator, AutoStartStatus expected)
      throws InterruptedException {
    var deadline = System.nanoTime() + 1_000_000_000L;
    while (startupCoordinator.autoStartStatus() != expected && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertEquals(expected, startupCoordinator.autoStartStatus());
  }

  private static ConnectInstanceInfo connectInfo(String instanceId) {
    return new ConnectInstanceInfo(instanceId, Instant.now(), 0, instanceId + "-reg", 1);
  }
}
