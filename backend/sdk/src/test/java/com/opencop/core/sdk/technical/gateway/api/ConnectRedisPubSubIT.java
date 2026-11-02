// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.push.PushMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test proving the remaining Redis PUSH pub/sub path.
 *
 * <p>Uses the configured component-test Redis endpoint, or starts a local Testcontainers Redis.
 */
class ConnectRedisPubSubIT {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;
  private static ObjectMapper objectMapper;

  private static final String TEST_INSTANCE_ID =
      "connect-test-" + UUID.randomUUID().toString().substring(0, 8);

  @BeforeAll
  static void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();

    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();

    objectMapper = new ObjectMapper();
  }

  @AfterAll
  static void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void pushPubSubRoundTrip() throws Exception {
    var receivedPush = new AtomicReference<String>();
    var latch = new CountDownLatch(1);

    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        (message, pattern) -> {
          receivedPush.set(new String(message.getBody(), StandardCharsets.UTF_8));
          latch.countDown();
        },
        new ChannelTopic(PushMessage.channelFor(TEST_INSTANCE_ID)));
    container.afterPropertiesSet();
    container.start();

    // Give the container time to subscribe
    Thread.sleep(500);

    try {
      var adapter = new ConnectServiceRedisAdapter(redisTemplate, objectMapper);
      var sourceId = UUID.randomUUID();
      var firstPayload = "push-one".getBytes(StandardCharsets.UTF_8);
      var secondPayload = "push-two".getBytes(StandardCharsets.UTF_8);

      adapter.pushOnInstance(sourceId, List.of(firstPayload, secondPayload), TEST_INSTANCE_ID);

      boolean received = latch.await(5, TimeUnit.SECONDS);
      assertThat(received).as("Subscriber should receive PUSH within 5 seconds").isTrue();

      var push = objectMapper.readValue(receivedPush.get(), PushMessage.class);
      assertThat(push.sourceId()).isEqualTo(sourceId);
      assertThat(push.payload()).hasSize(2);
      assertArrayEquals(firstPayload, push.payload().get(0));
      assertArrayEquals(secondPayload, push.payload().get(1));
    } finally {
      container.stop();
      container.destroy();
    }
  }
}
