// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.websocket.component.support.AbstractWebSocketFixtureIT;
import com.opencop.websocket.component.support.WebSocketComponentTestApplication;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.technical.gateway.cache.Janitor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketSession;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(WebSocketGeoConsumersClusterIT.ClusterFixtureCloser.class)
@SpringBootTest(
    classes = WebSocketComponentTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketGeoConsumersClusterIT extends AbstractWebSocketFixtureIT {

  private static final String USER_MESSAGES = "/user/queue/messages";
  private static final Map<String, String> PREVIOUS_REDIS_PROPERTIES = previousRedisProperties();
  private static final LocalRedisClusterSupport.Fixture REDIS_CLUSTER =
      LocalRedisClusterSupport.start();

  static {
    System.setProperty("redis.mode", "CLUSTER");
    System.setProperty("redis.host", REDIS_CLUSTER.seedHost());
    System.setProperty("redis.port", Integer.toString(REDIS_CLUSTER.seedPort()));
    System.setProperty("redis.ssl", "false");
  }

  private static volatile ConfigurableApplicationContext clusterApplicationContext;

  @Autowired private ConfigurableApplicationContext applicationContext;
  @Autowired private RedisEventGeoSourceDirectory sourceDirectory;

  @MockitoBean private Janitor janitor;

  private final Map<SourceKeys, List<String>> testKeys = new LinkedHashMap<>();

  @DynamicPropertySource
  static void registerClusterProperties(DynamicPropertyRegistry registry) {
    registerCommonWebSocketComponentProperties(registry);
    registry.add("redis.mode", () -> "CLUSTER");
    registry.add("redis.host", REDIS_CLUSTER::seedHost);
    registry.add("redis.port", REDIS_CLUSTER::seedPort);
    registry.add("redis.ssl", () -> "false");
  }

  @AfterEach
  void removeClusterTestData() {
    for (var entry : testKeys.entrySet()) {
      var keys = new ArrayList<>(entry.getValue());
      keys.add(entry.getKey().registryKey());
      keys.add(entry.getKey().updatedAtKey());
      keys.add(entry.getKey().staleAtKey());
      stringRedisTemplate.delete(keys);
    }
    testKeys.clear();
  }

  @Test
  void application_whenClustered_usesOneFactoryDirectoryAndNamedByteTemplate() {
    assertThat(eventGeoCacheKeyLayout.name()).isEqualTo("CLUSTER");
    assertThat(sourceDirectory.layout()).isEqualTo(eventGeoCacheKeyLayout);
    assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).hasSize(1);
    assertThat(applicationContext.getBeansOfType(RedisEventGeoSourceDirectory.class)).hasSize(1);
    assertThat(applicationContext.getBean("redisTemplate")).isSameAs(redisTemplate);

    var connectionFactory = applicationContext.getBean(RedisConnectionFactory.class);
    assertThat(connectionFactory).isSameAs(redisTemplate.getConnectionFactory());
    assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);
    var lettuce = (LettuceConnectionFactory) connectionFactory;
    assertThat(lettuce.isClusterAware()).isTrue();
    assertThat(lettuce.getClusterConfiguration().getClusterNodes())
        .extracting(node -> node.getHost() + ":" + node.getPort())
        .containsExactly(REDIS_CLUSTER.seedHost() + ":" + REDIS_CLUSTER.seedPort());
    String assemblyKey = eventGeoCacheKeyLayout.sourceKeys(UUID.randomUUID()).eventKey("assembly");
    byte[] assemblyPayload = bytes("byte-template");
    redisTemplate.opsForValue().set(assemblyKey, assemblyPayload);
    assertThat(redisTemplate.opsForValue().get(assemblyKey)).containsExactly(assemblyPayload);
    redisTemplate.delete(assemblyKey);
  }

  @Test
  void replay_sourcesAcrossPrimaries_preservesExactPayloads() throws Exception {
    Map<Integer, String> sourceIdsByPrimaryPort =
        REDIS_CLUSTER.sourceIdsByPrimaryPort(
            sourceId -> eventGeoCacheKeyLayout.sourceKeys(sourceId).registryKey());
    assertThat(sourceIdsByPrimaryPort).hasSize(3);

    var handler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), handler);
    UUID userId = awaitCurrentUserId();
    fixture.send(session, fixture.subscribe(USER_MESSAGES));

    var sourceKeys = new ArrayList<SourceKeys>();
    for (String sourceId : sourceIdsByPrimaryPort.values()) {
      UUID sourceUuid = UUID.fromString(sourceId);
      SourceKeys keys = trackSource(sourceId);
      sourceKeys.add(keys);
      seedActiveSourceAccess(sourceUuid, userId);
      stringRedisTemplate.opsForValue().set(keys.registryKey(), keys.sourcePrefix());
      seedIndexedEvent(keys, "first", bytes("first-" + sourceId), 1);
      seedIndexedEvent(keys, "last", bytes("last-" + sourceId), 2);
    }

    for (SourceKeys keys : sourceKeys) {
      UUID sourceId = UUID.fromString(keys.sourceId());
      fixture.send(session, fixture.subscribe(sourceId));

      assertState(awaitMessage(handler, "cluster replay start"), sourceId, "STARTING");
      var data = awaitMessage(handler, "cluster replay data");
      assertThat(data.getDestination()).isEqualTo(destination(sourceId));
      assertThat(data.getCompressed()).isFalse();
      assertThat(decodeMultiPayload(data))
          .containsExactly(bytes("first-" + sourceId), bytes("last-" + sourceId));
      assertState(awaitMessage(handler, "cluster replay finish"), sourceId, "FINISHED");
    }
    assertThat(handler.messageCount()).isZero();
  }

  @Test
  void notification_clusterSource_broadcastsExactPayload() throws Exception {
    Map<Integer, String> sourceIdsByPrimaryPort =
        REDIS_CLUSTER.sourceIdsByPrimaryPort(
            sourceId -> eventGeoCacheKeyLayout.sourceKeys(sourceId).registryKey());
    String sourceId = sourceIdsByPrimaryPort.values().stream().skip(1).findFirst().orElseThrow();
    UUID sourceUuid = UUID.fromString(sourceId);
    SourceKeys keys = trackSource(sourceId);
    byte[] payload = bytes("cluster-realtime-payload");
    testKeys.get(keys).add(keys.eventKey("realtime"));
    redisTemplate.opsForValue().set(keys.eventKey("realtime"), payload);

    var handler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), handler);
    seedActiveSourceAccess(sourceUuid, awaitCurrentUserId());
    fixture.send(session, fixture.subscribe(sourceUuid));
    await(
        () -> !sessionRegistry.getDestinationSessions(destination(sourceUuid)).isEmpty(),
        fixture.defaultTimeout(),
        "cluster realtime topic registration");

    var notification = new CacheEventsNotification(sourceUuid, List.of("realtime"));
    stringRedisTemplate.convertAndSend(
        CacheChannel.REAL_TIME_EVENTS.name(), objectMapper.writeValueAsString(notification));

    var delivered = awaitMessage(handler, "cluster realtime event");
    assertThat(delivered.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(delivered.getDestination()).isEqualTo(destination(sourceUuid));
    assertThat(delivered.getCompressed()).isFalse();
    assertThat(decodeMultiPayload(delivered)).containsExactly(payload);
    assertThat(session.isOpen()).isTrue();
  }

  private SourceKeys trackSource(String sourceId) {
    SourceKeys keys = eventGeoCacheKeyLayout.sourceKeys(sourceId);
    testKeys.putIfAbsent(keys, new ArrayList<>());
    return keys;
  }

  private void seedIndexedEvent(SourceKeys keys, String objectId, byte[] payload, double score) {
    String eventKey = keys.eventKey(objectId);
    testKeys.get(keys).add(eventKey);
    stringRedisTemplate.opsForZSet().add(keys.updatedAtKey(), eventKey, score);
    redisTemplate.opsForValue().set(eventKey, payload);
  }

  private static void assertState(SocketMessage message, UUID sourceId, String state)
      throws Exception {
    assertThat(message.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(message.getDestination()).isEqualTo(USER_MESSAGES);
    assertThat(new String(message.getPayload().toByteArray(), StandardCharsets.UTF_8))
        .contains("\"source\":\"" + sourceId + "\"")
        .contains("\"state\":\"" + state + "\"");
  }

  private static List<byte[]> decodeMultiPayload(SocketMessage message) throws Exception {
    return opencop.core.proto.websocket.SocketProtos.SocketMessage.MultiPayload.parseFrom(
            message.getPayload())
        .getPayloadList()
        .stream()
        .map(bytes -> bytes.toByteArray())
        .toList();
  }

  private static String destination(UUID sourceId) {
    return "/topic/queue/event/" + sourceId;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static Map<String, String> previousRedisProperties() {
    var properties = new LinkedHashMap<String, String>();
    for (String property : List.of("redis.mode", "redis.host", "redis.port", "redis.ssl")) {
      String previous = System.getProperty(property);
      if (previous != null) {
        properties.put(property, previous);
      }
    }
    return Map.copyOf(properties);
  }

  private static void restoreRedisProperties() {
    for (String property : List.of("redis.mode", "redis.host", "redis.port", "redis.ssl")) {
      String previous = PREVIOUS_REDIS_PROPERTIES.get(property);
      if (previous == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previous);
      }
    }
  }

  @Autowired
  void captureClusterApplicationContext(ConfigurableApplicationContext context) {
    clusterApplicationContext = context;
  }

  static final class ClusterFixtureCloser implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
      context
          .getStore(ExtensionContext.Namespace.create(WebSocketGeoConsumersClusterIT.class))
          .put(
              "cluster-fixture",
              (ExtensionContext.Store.CloseableResource)
                  () -> {
                    try {
                      if (clusterApplicationContext != null
                          && clusterApplicationContext.isActive()) {
                        clusterApplicationContext.close();
                      }
                    } finally {
                      try {
                        REDIS_CLUSTER.close();
                      } finally {
                        restoreRedisProperties();
                      }
                    }
                  });
    }
  }
}
