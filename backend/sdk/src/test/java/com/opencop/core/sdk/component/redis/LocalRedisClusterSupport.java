// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Owns one compact three-primary Redis OSS Cluster fixture for component tests. */
public final class LocalRedisClusterSupport {

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");
  private static final List<Integer> PORTS = List.of(17_100, 17_101, 17_102);
  private static final int SEED_PORT = PORTS.getFirst();
  private static final Path OWNERSHIP_PATH =
      Path.of(
          System.getProperty("java.io.tmpdir"), "opencop-java-redis-cluster-component-test.lock");
  private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration PORT_RELEASE_TIMEOUT = Duration.ofSeconds(10);
  private static final long SOURCE_ID_SEARCH_BOUND = 512L;
  private static final ObjectMapper JSON = new ObjectMapper();

  private LocalRedisClusterSupport() {}

  public static Fixture start() {
    return start(READINESS_TIMEOUT, container -> {});
  }

  static Fixture start(Duration readinessTimeout, Consumer<GenericContainer<?>> beforeReadiness) {
    var ownership = PortOwnership.acquire();
    FixedPortRedisContainer container = null;
    try {
      assertPortsAvailable();
      var runToken = UUID.randomUUID().toString();
      container = new FixedPortRedisContainer(runToken);
      container.start();
      var containerId = container.getContainerId();
      if (containerId == null || containerId.isBlank()) {
        throw new IllegalStateException("Redis cluster fixture started without a container ID");
      }
      var host = configureClientEndpoints(container);
      createCluster(container);
      beforeReadiness.accept(container);
      awaitReady(container, host, readinessTimeout);
      return new Fixture(container, containerId, ownership, host);
    } catch (RuntimeException | Error failure) {
      closeAfterFailedStart(container, ownership, failure);
      throw failure;
    }
  }

  private static void assertPortsAvailable() {
    for (int port : PORTS) {
      awaitPortAvailable(port);
    }
  }

  /**
   * Waits a bounded time for one fixed client port to become bindable.
   *
   * <p>Docker releases a fixed host port binding slightly after {@code container.stop()} returns,
   * so two component tests that each own this fixture in turn can otherwise collide even though
   * ownership transferred correctly. This waits for that release rather than assuming it; a port
   * still held after the bound fails with the same message as before, so a genuinely foreign owner
   * is still reported as ownership evidence instead of being bypassed.
   */
  private static void awaitPortAvailable(int port) {
    var deadline = System.nanoTime() + PORT_RELEASE_TIMEOUT.toNanos();
    while (true) {
      IOException lastFailure;
      try (var socket = new ServerSocket()) {
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress("127.0.0.1", port));
        return;
      } catch (IOException exception) {
        lastFailure = exception;
      }
      if (System.nanoTime() >= deadline) {
        throw new IllegalStateException(
            "Redis cluster fixture client port " + port + " is already in use", lastFailure);
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for Redis cluster fixture port " + port, exception);
      }
    }
  }

  private static String configureClientEndpoints(FixedPortRedisContainer container) {
    var host = container.getHost();
    if (host == null || host.isBlank() || host.indexOf(':') >= 0) {
      throw new IllegalStateException(
          "Redis cluster fixture requires a nonblank IPv4 address or hostname, but Testcontainers resolved "
              + host);
    }
    for (int port : PORTS) {
      checkedExec(
          container,
          "redis-cli",
          "-p",
          Integer.toString(port),
          "CONFIG",
          "SET",
          "cluster-announce-hostname",
          host);
      checkedExec(
          container,
          "redis-cli",
          "-p",
          Integer.toString(port),
          "CONFIG",
          "SET",
          "cluster-preferred-endpoint-type",
          "hostname");
    }
    return host;
  }

  private static void createCluster(FixedPortRedisContainer container) {
    checkedExec(
        container,
        "redis-cli",
        "--cluster",
        "create",
        "127.0.0.1:17100",
        "127.0.0.1:17101",
        "127.0.0.1:17102",
        "--cluster-replicas",
        "0",
        "--cluster-yes");
  }

  private static void awaitReady(FixedPortRedisContainer container, String host, Duration timeout) {
    var expectedEndpoints = PORTS.stream().map(port -> host + ":" + port).sorted().toList();
    var deadline = System.nanoTime() + timeout.toNanos();
    RuntimeException lastFailure = null;
    Readiness lastReadiness = null;
    while (System.nanoTime() < deadline) {
      try {
        lastReadiness = firstUnsettledView(container, deadline, expectedEndpoints);
        if (lastReadiness == null) {
          return;
        }
      } catch (ExecTimeoutException exception) {
        // Closing the exec stream does not kill its command. Stop the owned container instead
        // of retrying readiness and leaving more commands running after their timeouts.
        throw exception;
      } catch (RuntimeException exception) {
        lastFailure = exception;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for Redis Cluster", exception);
      }
    }
    throw new IllegalStateException(
        "Redis Cluster did not become ready with all slots and three advertised primaries on every node; last unsettled view="
            + lastReadiness,
        lastFailure);
  }

  /**
   * The first node whose view of the cluster is not ready yet, or null once every node's is. A
   * client builds its topology from any node, and a node learns another's announced hostname only
   * through gossip, so until then its view still names that primary by the IP the cluster was
   * created with, which a client outside the container cannot reach.
   */
  private static Readiness firstUnsettledView(
      FixedPortRedisContainer container, long deadline, List<String> expectedEndpoints) {
    for (int port : PORTS) {
      var view = readiness(container, deadline, port);
      if (!view.isReady(expectedEndpoints)) {
        return view;
      }
    }
    return null;
  }

  private static Readiness readiness(FixedPortRedisContainer container, long deadline, int port) {
    var nodePort = Integer.toString(port);
    var info = checkedExec(container, deadline, "redis-cli", "-p", nodePort, "cluster", "info");
    var state = infoValue(info, "cluster_state");
    var assignedSlots = Integer.parseInt(infoValue(info, "cluster_slots_assigned"));
    var knownNodes = Integer.parseInt(infoValue(info, "cluster_known_nodes"));
    return new Readiness(
        port, state, assignedSlots, knownNodes, primaryEndpoints(container, deadline, nodePort));
  }

  private static List<String> primaryEndpoints(
      FixedPortRedisContainer container, long deadline, String nodePort) {
    var result =
        checkedExec(
            container, deadline, "redis-cli", "--json", "-p", nodePort, "cluster", "shards");
    try {
      var shards = JSON.readTree(result);
      if (shards == null || !shards.isArray()) {
        throw new IllegalStateException("Redis CLUSTER SHARDS did not return an array");
      }
      var endpoints = new ArrayList<String>();
      for (var shard : shards) {
        var nodes = shard.path("nodes");
        if (!nodes.isArray()) {
          throw new IllegalStateException("Redis CLUSTER SHARDS omitted shard nodes");
        }
        for (var node : nodes) {
          if (!node.path("role").asText().equals("master")) {
            continue;
          }
          var endpoint = node.path("endpoint").asText();
          var port = node.path("port").asInt(-1);
          if (endpoint.isBlank() || port < 1 || port > 65_535) {
            throw new IllegalStateException(
                "Redis CLUSTER SHARDS returned an invalid primary endpoint: " + node);
          }
          endpoints.add(endpoint + ":" + port);
        }
      }
      return List.copyOf(endpoints);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to parse Redis CLUSTER SHARDS", exception);
    }
  }

  private static int slotOwnerPort(FixedPortRedisContainer container, String key) {
    var slotResult = checkedExec(container, "redis-cli", "-p", "17100", "cluster", "keyslot", key);
    var slot = Integer.parseInt(slotResult.trim());
    var nodesResult = checkedExec(container, "redis-cli", "-p", "17100", "cluster", "nodes");
    for (var line : nodesResult.lines().toList()) {
      var fields = line.trim().split("\\s+");
      if (fields.length < 9 || !fields[2].contains("master")) {
        continue;
      }
      for (int index = 8; index < fields.length; index++) {
        if (ownsSlot(fields[index], slot)) {
          return announcedPort(fields[1]);
        }
      }
    }
    throw new IllegalStateException("No Redis primary owns slot " + slot + " for key " + key);
  }

  private static boolean ownsSlot(String range, int slot) {
    if (range.startsWith("[")) {
      return false;
    }
    var separator = range.indexOf('-');
    if (separator < 0) {
      return Integer.parseInt(range) == slot;
    }
    var first = Integer.parseInt(range.substring(0, separator));
    var last = Integer.parseInt(range.substring(separator + 1));
    return slot >= first && slot <= last;
  }

  private static int announcedPort(String address) {
    var withoutBus = address.substring(0, address.indexOf('@'));
    return Integer.parseInt(withoutBus.substring(withoutBus.lastIndexOf(':') + 1));
  }

  private static String infoValue(String output, String key) {
    return output
        .lines()
        .map(String::trim)
        .filter(line -> line.startsWith(key + ":"))
        .map(line -> line.substring(key.length() + 1).trim())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing " + key + " in cluster info"));
  }

  private static String checkedExec(FixedPortRedisContainer container, String... command) {
    return checkedExec(container, System.nanoTime() + COMMAND_TIMEOUT.toNanos(), command);
  }

  static String checkedExec(GenericContainer<?> container, long deadline, String... command) {
    remainingNanos(deadline, command);
    var client = container.getDockerClient();
    var execId =
        client
            .execCreateCmd(container.getContainerId())
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(command)
            .exec()
            .getId();
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    try (var callback =
        new ResultCallback.Adapter<Frame>() {
          @Override
          public void onNext(Frame frame) {
            switch (frame.getStreamType()) {
              case STDOUT, RAW -> stdout.writeBytes(frame.getPayload());
              case STDERR -> stderr.writeBytes(frame.getPayload());
              case STDIN -> {}
            }
          }
        }) {
      remainingNanos(deadline, command);
      client.execStartCmd(execId).exec(callback);
      // Bound command completion using docker-java's own callback, without adding a worker.
      // Docker create/start/inspect calls and daemon teardown can still stall independently.
      if (!callback.awaitCompletion(remainingNanos(deadline, command), TimeUnit.NANOSECONDS)) {
        throw new ExecTimeoutException(command);
      }
      var exitCode = client.inspectExecCmd(execId).exec().getExitCodeLong();
      if (exitCode == null || exitCode != 0) {
        throw new IllegalStateException(
            "Redis cluster fixture command failed: "
                + String.join(" ", command)
                + "; stderr="
                + stderr.toString(StandardCharsets.UTF_8).trim());
      }
      return stdout.toString(StandardCharsets.UTF_8);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException(
          "Failed to execute Redis cluster fixture command: " + String.join(" ", command),
          exception);
    }
  }

  private static long remainingNanos(long deadline, String... command) {
    var remaining = deadline - System.nanoTime();
    if (remaining <= 0) {
      throw new ExecTimeoutException(command);
    }
    return remaining;
  }

  static final class ExecTimeoutException extends IllegalStateException {

    private ExecTimeoutException(String... command) {
      super("Timed out waiting for Redis cluster fixture command: " + String.join(" ", command));
    }
  }

  private static void closeAfterFailedStart(
      FixedPortRedisContainer container, PortOwnership ownership, Throwable failure) {
    if (container != null) {
      try {
        container.stop();
      } catch (RuntimeException | Error stopFailure) {
        failure.addSuppressed(stopFailure);
      }
    }
    try {
      ownership.close();
    } catch (RuntimeException | Error closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  /** One node's view of the cluster, reported by the node listening on {@code port}. */
  private record Readiness(
      int port, String state, int assignedSlots, int knownNodes, List<String> primaryEndpoints) {

    private Readiness {
      primaryEndpoints = List.copyOf(primaryEndpoints);
    }

    boolean isReady(List<String> expectedEndpoints) {
      return state.equals("ok")
          && assignedSlots == 16_384
          && knownNodes == PORTS.size()
          && primaryEndpoints.stream().sorted().toList().equals(expectedEndpoints);
    }
  }

  public static final class Fixture implements AutoCloseable {

    private final FixedPortRedisContainer container;
    private final String containerId;
    private final PortOwnership ownership;
    private final String host;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Fixture(
        FixedPortRedisContainer container,
        String containerId,
        PortOwnership ownership,
        String host) {
      this.container = container;
      this.containerId = containerId;
      this.ownership = ownership;
      this.host = host;
    }

    public String seedHost() {
      return host;
    }

    public int seedPort() {
      return SEED_PORT;
    }

    public void assertKeyOwnedByNonSeedPrimary(String key) {
      var ownerPort = slotOwnerPort(container, key);
      if (ownerPort == SEED_PORT) {
        throw new AssertionError("Expected key to be owned by a non-seed primary: " + key);
      }
    }

    /** Flushes only the script cache of the owned primary responsible for {@code key}. */
    public void flushScriptsOnPrimaryOwning(String key) {
      checkedExec(
          container,
          "redis-cli",
          "-p",
          Integer.toString(slotOwnerPort(container, key)),
          "SCRIPT",
          "FLUSH",
          "SYNC");
    }

    /** Returns whether {@code sha} exists on the owned primary responsible for {@code key}. */
    public boolean scriptExistsOnPrimaryOwning(String key, String sha) {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(sha, "sha");
      var result =
          checkedExec(
              container,
              "redis-cli",
              "-p",
              Integer.toString(slotOwnerPort(container, key)),
              "SCRIPT",
              "EXISTS",
              sha);
      return result.trim().equals("1");
    }

    /**
     * Deterministically picks one source id per actual primary.
     *
     * <p>Candidate ids are {@code new UUID(0L, ordinal)} for ordinals 1 through {@value
     * #SOURCE_ID_SEARCH_BOUND}. Each candidate's key is derived through {@code keyForSource} and
     * attributed to a primary with the fixture's own slot-owner lookup, so no test has to guess a
     * slot range, duplicate CRC logic, or rely on random ids landing well.
     *
     * @return an immutable map from actual primary port to source id, in fixture-port order
     */
    public Map<Integer, String> sourceIdsByPrimaryPort(Function<String, String> keyForSource) {
      Objects.requireNonNull(keyForSource, "keyForSource");
      var firstSourceByPort = new LinkedHashMap<Integer, String>();
      for (long ordinal = 1; ordinal <= SOURCE_ID_SEARCH_BOUND; ordinal++) {
        var sourceId = new UUID(0L, ordinal).toString();
        var key = keyForSource.apply(sourceId);
        if (key == null || key.isBlank()) {
          throw new IllegalArgumentException(
              "keyForSource returned a blank key for source id " + sourceId);
        }
        firstSourceByPort.putIfAbsent(slotOwnerPort(container, key), sourceId);
        if (firstSourceByPort.size() == PORTS.size()) {
          break;
        }
      }
      var byFixturePortOrder = new LinkedHashMap<Integer, String>();
      for (int port : PORTS) {
        var sourceId = firstSourceByPort.get(port);
        if (sourceId == null) {
          throw new AssertionError(
              "No source id within the first "
                  + SOURCE_ID_SEARCH_BOUND
                  + " candidates is owned by primary port "
                  + port
                  + "; covered ports were "
                  + firstSourceByPort.keySet());
        }
        byFixturePortOrder.put(port, sourceId);
      }
      return Collections.unmodifiableMap(byFixturePortOrder);
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      RuntimeException failure = null;
      try {
        container.stop();
      } catch (RuntimeException | Error exception) {
        failure =
            new IllegalStateException(
                "Failed to stop owned Redis cluster fixture " + containerId, exception);
      }
      try {
        ownership.close();
      } catch (RuntimeException | Error exception) {
        var closeFailure =
            new IllegalStateException(
                "Failed to release Redis cluster fixture ownership", exception);
        if (failure == null) {
          failure = closeFailure;
        } else {
          failure.addSuppressed(closeFailure);
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }

  private static final class PortOwnership implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private PortOwnership(FileChannel channel, FileLock lock) {
      this.channel = channel;
      this.lock = lock;
    }

    static PortOwnership acquire() {
      FileChannel channel = null;
      try {
        channel =
            FileChannel.open(OWNERSHIP_PATH, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var lock = channel.tryLock();
        if (lock == null) {
          channel.close();
          throw ownershipFailure(null);
        }
        return new PortOwnership(channel, lock);
      } catch (OverlappingFileLockException exception) {
        closeAfterFailedAcquire(channel, exception);
        throw ownershipFailure(exception);
      } catch (IOException exception) {
        closeAfterFailedAcquire(channel, exception);
        throw ownershipFailure(exception);
      }
    }

    private static IllegalStateException ownershipFailure(Exception cause) {
      return new IllegalStateException(
          "Redis cluster fixture ownership is already held at " + OWNERSHIP_PATH, cause);
    }

    private static void closeAfterFailedAcquire(FileChannel channel, Exception failure) {
      if (channel == null) {
        return;
      }
      try {
        channel.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
    }

    @Override
    public void close() {
      IOException failure = null;
      try {
        lock.release();
      } catch (IOException exception) {
        failure = exception;
      }
      try {
        channel.close();
      } catch (IOException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
      if (failure != null) {
        throw new IllegalStateException(
            "Failed to release Redis cluster fixture ownership", failure);
      }
    }
  }

  private static final class FixedPortRedisContainer
      extends GenericContainer<FixedPortRedisContainer> {

    private FixedPortRedisContainer(String runToken) {
      super(REDIS_IMAGE);
      for (int port : PORTS) {
        addFixedExposedPort(port, port);
      }
      withLabel("com.opencop.fixture", "redis-cluster-component-test");
      withLabel("com.opencop.fixture.run-token", runToken);
      withCopyFileToContainer(
          MountableFile.forClasspathResource("redis/cluster/start.sh", 05_55), "/probe/start.sh");
      withCommand("sh", "/probe/start.sh");
      waitingFor(
          Wait.forListeningPorts(PORTS.stream().mapToInt(Integer::intValue).toArray())
              .withStartupTimeout(READINESS_TIMEOUT));
    }
  }
}
