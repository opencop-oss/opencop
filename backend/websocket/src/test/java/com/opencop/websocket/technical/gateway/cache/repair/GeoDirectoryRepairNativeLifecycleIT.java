// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.websocket.technical.spring.config.GeoCacheConfiguration;
import com.opencop.websocket.technical.spring.config.WebSocketSchedulers;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.event.command.CommandListener;
import io.lettuce.core.event.command.CommandStartedEvent;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.ObjectMapper;

@Timeout(90)
class GeoDirectoryRepairNativeLifecycleIT {
  private static final Duration BOUND = Duration.ofSeconds(15);
  private static final String METRIC = "opencop_geo_directory_repair_";

  enum Stage {
    ACTIVATION,
    SCAN,
    SADD
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({
    GeoCacheConfiguration.class,
    WebSocketSchedulers.class,
    GeoDirectoryRepairConfiguration.class
  })
  static class ProbeApplication {}

  @org.junit.jupiter.api.Test
  void paddedClusterMode_createsClusterFactoryAndRepairFromCanonicalSelector() throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var probe = new Probe(fixture, " cLuStEr ")) {
      assertThat(probe.context.getEnvironment().getProperty("redis.mode")).isEqualTo("CLUSTER");
      assertThat(probe.context.getBean(EventGeoCacheKeyLayout.class))
          .isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
      assertThat(probe.factory.isClusterAware()).isTrue();
      assertThat(probe.context.getBean(GeoDirectoryRepairJob.class)).isSameAs(probe.job);
    }
    assertNoRepairThreads();
  }

  @ParameterizedTest
  @EnumSource(Stage.class)
  void contextClose_withNativeStageHeld_terminatesRepairAndFactory(Stage stage) throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var probe = new Probe(fixture)) {
      probe.arm(stage);
      probe.awaitHeldStage(stage);
      var stack = probe.repairThread().getStackTrace();
      if (stage == Stage.ACTIVATION)
        assertThat(List.of(stack).stream().map(StackTraceElement::getMethodName))
            .anyMatch(name -> name.equals("await") || name.contains("getConnection"));
      var start = System.nanoTime();
      var close = CompletableFuture.runAsync(probe.context::close);
      try {
        close.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
      } catch (java.util.concurrent.TimeoutException exception) {
        probe.record(
            stage + "-close-bound-exceeded", Duration.ofNanos(System.nanoTime() - start), stack);
        var stacks = new LinkedHashMap<String, List<String>>();
        Thread.getAllStackTraces()
            .forEach(
                (thread, frames) -> {
                  if (thread.getName().startsWith("geo-directory-repair-")
                      || List.of(frames).stream()
                          .anyMatch(
                              f ->
                                  f.getClassName().contains("DefaultLifecycleProcessor")
                                      || f.getClassName().contains("LettuceConnectionFactory")))
                    stacks.put(
                        thread.getName(),
                        List.of(frames).stream().map(StackTraceElement::toString).toList());
                });
        Files.writeString(
            Path.of("target", "native-lifecycle", stage + "-shutdown-stacks.json"),
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(stacks));
        probe.gates.values().forEach(RedisReplyGate::release);
        close.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
        throw exception;
      }
      await().atMost(BOUND).until(() -> !probe.repairThread().isAlive());
      var elapsed = Duration.ofNanos(System.nanoTime() - start);
      assertThat(elapsed).isLessThan(BOUND);
      probe.assertFailureStage("CANCELLED", stage);
      probe.record(stage + "-close", elapsed, stack);
    }
    assertNoRepairThreads();
  }

  @ParameterizedTest
  @EnumSource(
      value = Stage.class,
      names = {"SCAN", "SADD"})
  void commandTimeout_withReplyHeld_preservesFactoryAndNextScheduledPass(Stage stage)
      throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var probe = new Probe(fixture)) {
      var before = probe.lastSuccess();
      probe.arm(stage);
      probe.awaitHeldStage(stage);
      var stack = probe.repairThread().getStackTrace();
      var start = System.nanoTime();
      await().atMost(BOUND).until(() -> probe.passes("FAILED") == 1);
      var elapsed = Duration.ofNanos(System.nanoTime() - start);
      probe.stopJob();
      probe.assertFailureStage("FAILED", stage);
      assertThat(probe.lastSuccess()).isEqualTo(before);
      if (stage == Stage.SADD)
        assertThat(
                probe
                    .admin(probe.directoryPort)
                    .sync()
                    .sismember(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET, probe.source))
            .isTrue();
      probe.gates.values().forEach(RedisReplyGate::release);
      probe.targetCommand.set(null);
      var redis = probe.context.getBean(StringRedisTemplate.class);
      redis.opsForValue().set("native-lifecycle-health", "healthy");
      assertThat(redis.opsForValue().get("native-lifecycle-health")).isEqualTo("healthy");
      probe.job.start();
      await().atMost(Duration.ofSeconds(25)).until(() -> probe.passes("COMPLETE") >= 1);
      probe.stopJob();
      assertThat(probe.lastSuccess()).isGreaterThan(before);
      probe.record(stage + "-timeout", elapsed, stack);
    }
    assertNoRepairThreads();
  }

  @org.junit.jupiter.api.Test
  void stop_withActivationHeld_preservesSharedAcquisitionAndNextPass() throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var probe = new Probe(fixture)) {
      probe.arm(Stage.ACTIVATION);
      probe.awaitHeldStage(Stage.ACTIVATION);
      var node =
          probe.nodes.stream()
              .filter(n -> n.getUri().getPort() == probe.targetPort)
              .findFirst()
              .orElseThrow();
      var sharedAcquisition =
          probe.nativeCommands().getStatefulConnection().getConnectionAsync(node.getNodeId());
      assertThat(sharedAcquisition.isDone()).isFalse();
      var start = System.nanoTime();
      var stack = probe.repairThread().getStackTrace();
      probe.stopJob();
      var elapsed = Duration.ofNanos(System.nanoTime() - start);
      assertThat(elapsed).isLessThan(BOUND);
      assertThat(sharedAcquisition.isDone()).isFalse();
      assertThat(probe.passes("CANCELLED")).isEqualTo(1);
      probe.assertFailureStage("CANCELLED", Stage.ACTIVATION);
      probe.gates.values().forEach(RedisReplyGate::release);
      var sharedConnection = sharedAcquisition.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
      assertThat(sharedConnection.isOpen()).isTrue();
      var source =
          fixture
              .sourceIdsByPrimaryPort(
                  s -> EventGeoCacheKeyLayout.CLUSTER.sourceKeys(s).registryKey())
              .get(probe.targetPort);
      var key = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(source).eventKey("health");
      var redis = probe.context.getBean(StringRedisTemplate.class);
      redis.opsForValue().set(key, "healthy");
      assertThat(redis.opsForValue().get(key)).isEqualTo("healthy");
      probe.job.start();
      await().atMost(Duration.ofSeconds(25)).until(() -> probe.passes("COMPLETE") >= 1);
      probe.stopJob();
      probe.record("ACTIVATION-stop-recovery", elapsed, stack);
    }
    assertNoRepairThreads();
  }

  private static void assertNoRepairThreads() {
    assertThat(Thread.getAllStackTraces().keySet())
        .noneMatch(t -> t.isAlive() && t.getName().startsWith("geo-directory-repair-"));
  }

  private static final class Probe implements AutoCloseable {
    final ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GeoDirectoryRepairJob.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    final Map<Integer, RedisReplyGate> gates = new LinkedHashMap<>();
    final Map<Integer, StatefulRedisConnection<String, String>> admins = new LinkedHashMap<>();
    final List<RedisClient> adminClients = new ArrayList<>();
    final AtomicReference<String> targetCommand = new AtomicReference<>();
    final AtomicReference<Thread> worker = new AtomicReference<>();
    final AtomicReference<Long> commandStarted = new AtomicReference<>();
    ConfigurableApplicationContext context;
    GeoDirectoryRepairJob job;
    LettuceConnectionFactory factory;
    io.lettuce.core.ClientOptions options;
    MeterRegistry metrics;
    List<RedisClusterNode> nodes;
    int targetPort;
    int directoryPort;
    int acceptedBefore;
    long serverCountBefore;
    String source;

    Probe(LocalRedisClusterSupport.Fixture fixture) throws Exception {
      this(fixture, "CLUSTER");
    }

    Probe(LocalRedisClusterSupport.Fixture fixture, String mode) throws Exception {
      try {
        for (int port : List.of(17_100, 17_101, 17_102)) {
          gates.put(port, new RedisReplyGate(fixture.seedHost(), port));
          var client = RedisClient.create(RedisURI.create(fixture.seedHost(), port));
          adminClients.add(client);
          admins.put(port, client.connect());
        }
        var app = SpringApplicationFactory.create(ProbeApplication.class);
        app.setLogStartupInfo(false);
        app.addInitializers(
            c -> {
              c.getBeanFactory()
                  .registerSingleton(
                      "replyGateRouting",
                      (ClientResourcesBuilderCustomizer)
                          builder ->
                              builder.socketAddressResolver(
                                  MappingSocketAddressResolver.create(
                                      DnsResolvers.UNRESOLVED,
                                      address ->
                                          HostAndPort.of(
                                              "127.0.0.1", gates.get(address.getPort()).port()))));
              c.getBeanFactory()
                  .addBeanPostProcessor(
                      new org.springframework.beans.factory.config.BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String name) {
                          if (bean instanceof LettuceConnectionFactory lettuce)
                            lettuce
                                .getNativeClient()
                                .addListener(
                                    new CommandListener() {
                                      @Override
                                      public void commandStarted(CommandStartedEvent event) {
                                        if (Thread.currentThread()
                                            .getName()
                                            .startsWith("geo-directory-repair-"))
                                          worker.set(Thread.currentThread());
                                        if (event
                                            .getCommand()
                                            .getType()
                                            .name()
                                            .equals(targetCommand.get())) {
                                          gates.get(targetPort).holdReplies();
                                          commandStarted.compareAndSet(null, System.nanoTime());
                                        }
                                      }
                                    });
                          return bean;
                        }
                      });
            });
        context =
            app.run(
                "--spring.main.web-application-type=none",
                "--spring.config.name=native-directory-proof",
                "--spring.main.banner-mode=off",
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                "--redis.mode=" + mode,
                "--redis.host=" + fixture.seedHost(),
                "--redis.port=" + fixture.seedPort(),
                "--redis.ssl=false",
                "--opencop.geo.directory-repair.cron=*/20 * * * * *",
                "--janitor.interval.ms=300000");
        job = context.getBean(GeoDirectoryRepairJob.class);
        stopJob();
        logs.list = new CopyOnWriteArrayList<>();
        logs.start();
        logger.addAppender(logs);
        factory = context.getBean(LettuceConnectionFactory.class);
        metrics = context.getBean(MeterRegistry.class);
        var client = (RedisClusterClient) factory.getNativeClient();
        options = client.getOptions();
        var commands = nativeCommands();
        nodes =
            commands.getStatefulConnection().getPartitions().stream()
                .filter(n -> n.is(RedisClusterNode.NodeFlag.UPSTREAM))
                .sorted(Comparator.comparing(RedisClusterNode::getNodeId))
                .toList();
        directoryPort =
            commands
                .getStatefulConnection()
                .getPartitions()
                .getPartitionBySlot(
                    SlotHash.getSlot(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET))
                .getUri()
                .getPort();
        // Prepare markers with the independent direct client; no directory or production-client
        // warmup.
        var byPort =
            fixture.sourceIdsByPrimaryPort(
                s -> EventGeoCacheKeyLayout.CLUSTER.sourceKeys(s).registryKey());
        source = byPort.get(nodes.getFirst().getUri().getPort());
        var keys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(source);
        admin(nodes.getFirst().getUri().getPort())
            .sync()
            .set(keys.registryKey(), keys.sourcePrefix());
      } catch (Exception | Error exception) {
        close();
        throw exception;
      }
    }

    @SuppressWarnings("unchecked")
    RedisAdvancedClusterAsyncCommands<byte[], byte[]> nativeCommands() {
      try (var spring = (LettuceConnection) factory.getConnection()) {
        return (RedisAdvancedClusterAsyncCommands<byte[], byte[]>) spring.getNativeConnection();
      }
    }

    StatefulRedisConnection<String, String> admin(int port) {
      return admins.get(port);
    }

    void arm(Stage stage) throws Exception {
      var commands = nativeCommands();
      if (stage == Stage.ACTIVATION) {
        targetPort =
            nodes.stream()
                .filter(n -> n.getUri().getPort() != directoryPort)
                .reduce((a, b) -> b)
                .orElseThrow()
                .getUri()
                .getPort();
        acceptedBefore = gates.get(targetPort).accepted();
        serverCountBefore = serverCount("hello") + serverCount("ping");
        gates.get(targetPort).holdNewConnections();
      } else {
        // Activate each native node connection before gating command responses.
        for (var node : nodes) commands.getConnection(node.getNodeId()).ping().get();
        commands.get("native-lifecycle-health".getBytes(StandardCharsets.UTF_8)).get();
        commands
            .sismember(
                RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET.getBytes(StandardCharsets.UTF_8),
                "absent".getBytes(StandardCharsets.UTF_8))
            .get();
        targetPort = stage == Stage.SADD ? directoryPort : nodes.getFirst().getUri().getPort();
        serverCountBefore = serverCount(stage.name().toLowerCase());
        targetCommand.set(stage.name());
      }
      job.start();
    }

    void awaitHeldStage(Stage stage) {
      await()
          .pollInterval(Duration.ofMillis(10))
          .atMost(Duration.ofSeconds(21))
          .until(
              () -> {
                if (stage == Stage.ACTIVATION) {
                  var thread =
                      Thread.getAllStackTraces().keySet().stream()
                          .filter(t -> t.getName().startsWith("geo-directory-repair-"))
                          .filter(
                              t ->
                                  List.of(t.getStackTrace()).stream()
                                      .anyMatch(
                                          frame ->
                                              frame
                                                      .getClassName()
                                                      .endsWith("RedisGeoDirectoryRepair")
                                                  && frame.getMethodName().equals("await")))
                          .findFirst();
                  thread.ifPresent(worker::set);
                  return thread.isPresent()
                      && gates.get(targetPort).accepted() > acceptedBefore
                      && serverCount("hello") + serverCount("ping") > serverCountBefore;
                }
                return commandStarted.get() != null
                    && serverCount(stage.name().toLowerCase()) > serverCountBefore;
              });
      assertThat(gates.get(targetPort).failure()).isNull();
    }

    long serverCount(String command) {
      return admin(targetPort)
          .sync()
          .info("commandstats")
          .lines()
          .filter(line -> line.startsWith("cmdstat_" + command + ":"))
          .mapToLong(line -> Long.parseLong(line.split("calls=")[1].split(",")[0]))
          .findFirst()
          .orElse(0L);
    }

    Thread repairThread() {
      return java.util.Objects.requireNonNull(worker.get());
    }

    double lastSuccess() {
      return metrics.get(METRIC + "last_success_timestamp_seconds").gauge().value();
    }

    long passes(String outcome) {
      var timer = metrics.find(METRIC + "duration_seconds").tag("outcome", outcome).timer();
      return timer == null ? 0 : timer.count();
    }

    void stopJob() {
      job.stop();
      var scheduler = context.getBean("geoDirectoryRepairScheduler", ThreadPoolTaskScheduler.class);
      await().atMost(BOUND).until(() -> scheduler.getActiveCount() == 0);
    }

    void assertFailureStage(String outcome, Stage heldStage) {
      var expected =
          switch (heldStage) {
            case ACTIVATION -> "ACQUIRING";
            case SCAN -> "SCANNING";
            case SADD -> "REGISTERING";
          };
      assertThat(logs.list)
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .anySatisfy(
              event -> {
                assertThat(event.getFormattedMessage())
                    .contains("outcome=" + outcome, "stage=" + expected);
                assertThat(event.getThrowableProxy()).isNull();
              });
    }

    void record(String scenario, Duration elapsed, StackTraceElement[] stack) throws Exception {
      var receipt = new LinkedHashMap<String, Object>();
      receipt.put("scenario", scenario);
      receipt.put("elapsedMillis", elapsed.toMillis());
      receipt.put(
          "factoryCommandTimeout", factory.getClientConfiguration().getCommandTimeout().toString());
      receipt.put(
          "socketConnectTimeout", options.getSocketOptions().getConnectTimeout().toString());
      receipt.put("nativeCommandTimeoutEnabled", options.getTimeoutOptions().isTimeoutCommands());
      var timeoutSource = options.getTimeoutOptions().getSource();
      receipt.put(
          "nativeCommandTimeoutNanos",
          timeoutSource.getTimeUnit().toNanos(timeoutSource.getTimeout(null)));
      receipt.put("cron", "*/20 * * * * *");
      receipt.put("stack", List.of(stack).stream().map(StackTraceElement::toString).toList());
      receipt.put("failed", passes("FAILED"));
      receipt.put("cancelled", passes("CANCELLED"));
      receipt.put("complete", passes("COMPLETE"));
      var path = Path.of("target", "native-lifecycle", scenario + ".json");
      Files.createDirectories(path.getParent());
      Files.writeString(
          path, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
    }

    @Override
    public void close() throws Exception {
      try {
        if (context != null) context.close();
      } finally {
        logger.detachAppender(logs);
        logs.stop();
        for (var gate : gates.values()) gate.close();
        admins.values().forEach(StatefulRedisConnection::close);
        adminClients.forEach(RedisClient::shutdown);
      }
    }
  }
}
