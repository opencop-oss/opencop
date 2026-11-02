// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisGeoDirectoryRepair.Stage;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisGeoDirectoryRepairTest {

  @ParameterizedTest
  @EnumSource(Stage.class)
  void repair_operationThrows_reportsEnteredStageAndPreservesFailure(Stage stage) throws Exception {
    var fixture = new Fixture();
    var failure = new IllegalStateException("injected operation failure");
    var pacing = fixture.failAt(stage, failure);
    var progress = new RedisGeoDirectoryRepair.Progress();

    assertThatThrownBy(() -> fixture.repair.repair(10, 10, pacing, progress)).isSameAs(failure);

    assertThat(progress.stage()).isEqualTo(stage);
    assertThat(failure.getSuppressed()).isEmpty();
    if (stage == Stage.INITIALIZING) {
      verify(fixture.spring, never()).close();
    } else {
      verify(fixture.spring).close();
    }
    verify(fixture.connection, never()).close();
    verify(fixture.nodeConnection, never()).close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repair_scanAndOptionalCloseFailure_preservesOriginalStageAndPrimaryException(
      boolean closeFails) throws Exception {
    var fixture = new Fixture();
    var primaryFailure = new IllegalStateException("scan failure");
    var closeFailure = new IllegalArgumentException("close failure");
    var pacing = fixture.failAt(Stage.SCANNING, primaryFailure);
    if (closeFails) doThrow(closeFailure).when(fixture.spring).close();
    var progress = new RedisGeoDirectoryRepair.Progress();

    assertThatThrownBy(() -> fixture.repair.repair(10, 10, pacing, progress))
        .isSameAs(primaryFailure);

    assertThat(progress.stage()).isEqualTo(Stage.SCANNING);
    assertThat(progress.scans()).isEqualTo(1);
    assertThat(primaryFailure.getSuppressed())
        .containsExactly(closeFails ? new Throwable[] {closeFailure} : new Throwable[0]);
    verify(fixture.spring).close();
  }

  private static final class Fixture {
    private static final String NODE_ID = "primary";

    private final LettuceConnectionFactory factory = mock();
    private final LettuceConnection spring = mock();
    private final RedisAdvancedClusterAsyncCommands<byte[], byte[]> cluster = mock();
    private final StatefulRedisClusterConnection<byte[], byte[]> connection = mock();
    private final StatefulRedisConnection<byte[], byte[]> nodeConnection = mock();
    private final RedisAsyncCommands<byte[], byte[]> commands = mock();
    private final Partitions partitions = new Partitions();
    private final KeyScanCursor<byte[]> page = new KeyScanCursor<>();
    private final RedisGeoDirectoryRepair repair;

    @SuppressWarnings("deprecation")
    private Fixture() throws Exception {
      var primary = new RedisClusterNode();
      primary.setNodeId(NODE_ID);
      primary.setFlags(Set.of(RedisClusterNode.NodeFlag.UPSTREAM));
      primary.setSlots(List.of(1));
      partitions.add(primary);
      page.setFinished(true);
      when(factory.isClusterAware()).thenReturn(true);
      when(factory.getConnection()).thenReturn(spring);
      when(factory.getClientConfiguration())
          .thenReturn(LettuceClientConfiguration.defaultConfiguration());
      when(spring.getNativeConnection()).thenReturn(cluster);
      when(cluster.getStatefulConnection()).thenReturn(connection);
      when(connection.getPartitions()).thenReturn(partitions);
      when(connection.getConnectionAsync(NODE_ID))
          .thenReturn(CompletableFuture.completedFuture(nodeConnection));
      when(nodeConnection.async()).thenReturn(commands);
      RedisFuture<KeyScanCursor<byte[]>> scan = mock();
      when(scan.get(anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(page);
      when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(scan);
      repair = new RedisGeoDirectoryRepair(factory);
    }

    private RedisGeoDirectoryRepair.BetweenPages failAt(Stage stage, RuntimeException failure) {
      switch (stage) {
        case INITIALIZING -> when(factory.getConnection()).thenThrow(failure);
        case INITIAL_TOPOLOGY -> when(connection.getPartitions()).thenThrow(failure);
        case ACQUIRING -> when(connection.getConnectionAsync(NODE_ID)).thenThrow(failure);
        case PACING -> {
          page.setFinished(false);
          return () -> {
            throw failure;
          };
        }
        case SCANNING ->
            when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenThrow(failure);
        case REGISTERING -> {
          var source = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(new UUID(0L, 1L));
          page.getKeys().add(source.registryKey().getBytes(StandardCharsets.UTF_8));
          when(cluster.sadd(any(byte[].class), any(byte[][].class))).thenThrow(failure);
        }
        case FINAL_TOPOLOGY ->
            when(connection.getPartitions()).thenReturn(partitions).thenThrow(failure);
        case CLOSING -> doThrow(failure).when(spring).close();
      }
      return () -> {};
    }
  }
}
