// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisPublisher;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobLifecycleUpdater;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalChunkDeliveryStateRepository;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobDeliveryRepository;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobLifecycleStateRepository;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobLifecycleUpdater;
import com.opencop.websocket.ports.business.usecase.event.EventHandler;
import com.opencop.websocket.ports.business.usecase.history.HistoryEventHandler;
import com.opencop.websocket.ports.business.usecase.notification.NotificationHandler;
import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.listener.HistoryEventsListener;
import com.opencop.websocket.technical.spring.listener.NotificationsListener;
import com.opencop.websocket.technical.spring.listener.RealTimeEventsListener;
import com.opencop.websocket.technical.spring.listener.RedisListenerRegistration;
import com.opencop.websocket.technical.spring.listener.WatchboxAlertsListener;
import com.opencop.websocket.technical.spring.websocket.WatchboxSubscriptionReauthorizer;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class CacheConfig {

  @Bean
  public CachePublisher<HistoryEvent> historyEventsPublisher(
      ObjectMapper objectMapper, StringRedisTemplate stringRedisTemplate) {
    return new RedisPublisher<>(objectMapper, CacheChannel.HISTORY_EVENTS, stringRedisTemplate);
  }

  @Bean
  public RedisHistoricalJobDeliveryRepository redisHistoricalJobDeliveryRepository(
      StringRedisTemplate stringRedisTemplate) {
    return new RedisHistoricalJobDeliveryRepository(stringRedisTemplate);
  }

  @Bean
  public HistoryJobDeliveryCoordinator historyJobDeliveryCoordinator(
      RedisHistoricalJobDeliveryRepository redisHistoricalJobDeliveryRepository,
      HistoricalJobLifecycleUpdater historicalJobLifecycleUpdater,
      CachePublisher<HistoryEvent> historyEventsPublisher) {
    return new HistoryJobDeliveryCoordinator(
        redisHistoricalJobDeliveryRepository,
        historicalJobLifecycleUpdater,
        historyEventsPublisher,
        Duration.ofHours(24));
  }

  @Bean
  public RedisHistoricalJobLifecycleStateRepository redisHistoricalJobLifecycleStateRepository(
      StringRedisTemplate stringRedisTemplate, HistoricalJobControlKeyLayout keyLayout) {
    return new RedisHistoricalJobLifecycleStateRepository(stringRedisTemplate, keyLayout);
  }

  @Bean
  public HistoricalJobLifecycleUpdater historicalJobLifecycleUpdater(
      RedisHistoricalJobLifecycleStateRepository redisHistoricalJobLifecycleStateRepository) {
    return new RedisHistoricalJobLifecycleUpdater(redisHistoricalJobLifecycleStateRepository);
  }

  @Bean
  public RedisHistoricalChunkDeliveryStateRepository redisHistoricalChunkDeliveryStateRepository(
      StringRedisTemplate stringRedisTemplate) {
    return new RedisHistoricalChunkDeliveryStateRepository(stringRedisTemplate);
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, List<RedisListenerRegistration> registrations) {
    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);

    for (var reg : registrations) {
      container.addMessageListener(reg.listener(), reg.topic());
    }

    return container;
  }

  @Bean
  public RedisListenerRegistration realTimeEventsListener(
      ObjectMapper om, EventHandler<List<String>> handler) {
    return new RedisListenerRegistration(
        new RealTimeEventsListener(om, handler),
        new ChannelTopic(CacheChannel.REAL_TIME_EVENTS.name()));
  }

  @Bean
  public RedisListenerRegistration notificationsListener(
      ObjectMapper om, NotificationHandler handler) {
    return new RedisListenerRegistration(
        new NotificationsListener(om, handler),
        new ChannelTopic(CacheChannel.OPENCOP_NOTIFICATIONS.name()));
  }

  @Bean
  public RedisListenerRegistration historyEventsListener(
      ObjectMapper om, HistoryEventHandler handler) {
    return new RedisListenerRegistration(
        new HistoryEventsListener(om, handler),
        new ChannelTopic(CacheChannel.HISTORY_EVENTS.name()));
  }

  @Bean
  WatchboxSubscriptionReauthorizer watchboxSubscriptionReauthorizer(
      WsSessionRegistry sessionRegistry,
      WatchboxSubscribeAuthorizer watchboxSubscribeAuthorizer,
      MeterRegistry meterRegistry,
      Clock clock,
      @Value("${opencop.watchbox.subscription-reauth-interval}") Duration reauthInterval) {
    return new WatchboxSubscriptionReauthorizer(
        sessionRegistry,
        watchboxSubscribeAuthorizer,
        meterRegistry,
        clock,
        reauthInterval,
        System::nanoTime);
  }

  @Bean
  RedisListenerRegistration watchboxAlertsListener(
      ObjectMapper om,
      SocketProducer socketProducer,
      WatchboxSubscriptionReauthorizer watchboxSubscriptionReauthorizer) {
    return new RedisListenerRegistration(
        new WatchboxAlertsListener(om, socketProducer, watchboxSubscriptionReauthorizer),
        new ChannelTopic(CacheChannel.WATCHBOX_ALERTS.name()));
  }
}
