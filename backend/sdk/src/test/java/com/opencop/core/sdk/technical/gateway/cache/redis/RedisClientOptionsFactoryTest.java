// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisClientOptionsFactoryTest {

  @Test
  void applyHardening_clusterBuilder_preservesClusterSettingsAndAddsNetworkOptions() {
    var topologyRefreshOptions =
        ClusterTopologyRefreshOptions.builder()
            .enableAllAdaptiveRefreshTriggers()
            .enablePeriodicRefresh(Duration.ofSeconds(17))
            .dynamicRefreshSources(false)
            .build();
    var builder =
        ClusterClientOptions.builder()
            .maxRedirects(11)
            .topologyRefreshOptions(topologyRefreshOptions);

    RedisClientOptionsFactory.applyHardening(builder);

    var clientOptions = builder.build();
    var socketOptions = clientOptions.getSocketOptions();

    assertEquals(11, clientOptions.getMaxRedirects());
    assertEquals(topologyRefreshOptions, clientOptions.getTopologyRefreshOptions());
    assertTrue(clientOptions.isAutoReconnect());
    assertTrue(clientOptions.isPingBeforeActivateConnection());
    assertTrue(clientOptions.getTimeoutOptions().isTimeoutCommands());
    assertEquals(Duration.ofSeconds(5), socketOptions.getConnectTimeout());
    assertTrue(socketOptions.getKeepAlive().isEnabled());
    assertEquals(Duration.ofSeconds(30), socketOptions.getKeepAlive().getIdle());
    assertEquals(Duration.ofSeconds(10), socketOptions.getKeepAlive().getInterval());
    assertEquals(3, socketOptions.getKeepAlive().getCount());
  }
}
