// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/** Page-level native access keeps empty SCAN pages visible to the caller's pacing policy. */
public final class RedisGeoDirectoryRepair {
  private final LettuceConnectionFactory factory;

  public RedisGeoDirectoryRepair(LettuceConnectionFactory factory) {
    if (!factory.isClusterAware())
      throw new IllegalArgumentException("Repair requires Redis Cluster");
    this.factory = factory;
  }

  @FunctionalInterface
  public interface BetweenPages {
    void pause() throws InterruptedException;
  }

  /** Last-entered operation; a failed operation retains its stage during wrapper cleanup. */
  public enum Stage {
    /** Validate the pass and borrow the shared cluster connection through its Spring wrapper. */
    INITIALIZING,
    /** Snapshot the primaries and slots that this pass will visit. */
    INITIAL_TOPOLOGY,
    /** Await a primary's connection without cancelling Lettuce's shared acquisition future. */
    ACQUIRING,
    /** Pause after the preceding page has completed, including across primary transitions. */
    PACING,
    /** Request and await the next page of registry marker keys. */
    SCANNING,
    /** Add a source batch to the candidate directory and await its acknowledgement. */
    REGISTERING,
    /** Compare the ending topology with the initial snapshot before declaring success. */
    FINAL_TOPOLOGY,
    /** Close the borrowed wrapper after the scan and final topology check returned normally. */
    CLOSING
  }

  /** A pass-local mutable result, owned only by its calling worker, including on failure. */
  public static final class Progress {
    private long scans;
    private long restored;
    private long malformed;
    private Stage stage = Stage.INITIALIZING;

    public long scans() {
      return scans;
    }

    public long restored() {
      return restored;
    }

    public long malformed() {
      return malformed;
    }

    public Stage stage() {
      return stage;
    }
  }

  public enum Outcome {
    /** Every observed primary was scanned and the ending topology snapshot matched. */
    COMPLETE,
    /** Additions are retained, but a changed topology snapshot prevents reporting success. */
    TOPOLOGY_CHANGED
  }

  public Outcome repair(int scanCount, int batchSize, BetweenPages pacing, Progress progress)
      throws InterruptedException {
    if (scanCount <= 0 || batchSize <= 0)
      throw new IllegalArgumentException("Positive limits required");
    // Closing the borrowed Spring wrapper does not close Boot's shared native connection.
    try (var spring = (LettuceConnection) factory.getConnection()) {
      var cluster =
          (RedisAdvancedClusterAsyncCommands<byte[], byte[]>) spring.getNativeConnection();
      var connection = borrowedConnection(cluster);
      progress.stage = Stage.INITIAL_TOPOLOGY;
      var before = topology(connection);
      if (before.isEmpty())
        throw new DataAccessResourceFailureException("No Redis primaries available");
      boolean first = true;
      for (var nodeId : before.keySet()) {
        // The synchronous node lookup joins uninterruptibly. Cancel only our derived wait,
        // never the shared connection future owned by Lettuce/Boot.
        progress.stage = Stage.ACQUIRING;
        var commands = await(connection.getConnectionAsync(nodeId).thenApply(node -> node.async()));
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
          if (!first) {
            progress.stage = Stage.PACING;
            pacing.pause();
          }
          first = false;
          progress.stage = Stage.SCANNING;
          assertNotInterrupted();
          progress.scans++;
          var page =
              await(
                  commands.scan(
                      cursor,
                      ScanArgs.Builder.matches(EventGeoCacheKeyLayout.CLUSTER.markerScanPattern())
                          .limit(scanCount)));
          registerMarkers(cluster, page.getKeys(), batchSize, progress);
          cursor = page;
        } while (!cursor.isFinished());
      }
      progress.stage = Stage.FINAL_TOPOLOGY;
      var outcome =
          before.equals(topology(connection)) ? Outcome.COMPLETE : Outcome.TOPOLOGY_CHANGED;
      progress.stage = Stage.CLOSING;
      return outcome;
    }
  }

  @SuppressWarnings("deprecation")
  private static StatefulRedisClusterConnection<byte[], byte[]> borrowedConnection(
      RedisAdvancedClusterAsyncCommands<byte[], byte[]> cluster) {
    // Lettuce 6.8 has no equivalent interruptible node acquisition on the commands interface.
    // Borrow its stateful connection to await/cancel only our derived future; Boot owns it.
    // Revisit this accessor on the next Lettuce upgrade rather than using a synchronous join.
    return cluster.getStatefulConnection();
  }

  private void registerMarkers(
      RedisAdvancedClusterAsyncCommands<byte[], byte[]> cluster,
      List<byte[]> markers,
      int batchSize,
      Progress progress)
      throws InterruptedException {
    var batch = new LinkedHashSet<String>();
    for (var marker : markers) {
      try {
        batch.add(
            EventGeoCacheKeyLayout.CLUSTER
                .sourceKeysFromDirectoryEntry(decodeMarker(marker))
                .sourceId());
      } catch (CharacterCodingException | IllegalArgumentException exception) {
        progress.malformed++;
        continue;
      }
      if (batch.size() == batchSize) {
        add(cluster, batch, progress);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) add(cluster, batch, progress);
  }

  private static String decodeMarker(byte[] marker) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(marker))
        .toString();
  }

  private void add(
      RedisAdvancedClusterAsyncCommands<byte[], byte[]> cluster,
      LinkedHashSet<String> ids,
      Progress progress)
      throws InterruptedException {
    progress.stage = Stage.REGISTERING;
    assertNotInterrupted();
    var values = ids.stream().map(s -> s.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
    progress.restored +=
        await(
            cluster.sadd(
                RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET.getBytes(StandardCharsets.UTF_8),
                values));
  }

  private <T> T await(Future<T> future) throws InterruptedException {
    try {
      return future.get(
          factory.getClientConfiguration().getCommandTimeout().toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw exception;
    } catch (TimeoutException exception) {
      future.cancel(true);
      throw new QueryTimeoutException("Geo directory repair command timed out", exception);
    } catch (ExecutionException exception) {
      throw new DataAccessResourceFailureException(
          "Geo directory repair command failed", exception.getCause());
    }
  }

  private static void assertNotInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
  }

  private static Map<String, List<Integer>> topology(
      StatefulRedisClusterConnection<byte[], byte[]> connection) {
    var snapshot = new TreeMap<String, List<Integer>>();
    for (var node : connection.getPartitions()) {
      if (node.is(RedisClusterNode.NodeFlag.UPSTREAM))
        snapshot.put(node.getNodeId(), List.copyOf(node.getSlots()));
    }
    return snapshot;
  }
}
