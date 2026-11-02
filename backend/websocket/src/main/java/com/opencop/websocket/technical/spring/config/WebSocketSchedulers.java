// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class WebSocketSchedulers {
  @Bean("wsAuthExpiryScheduler")
  ThreadPoolTaskScheduler wsAuthExpiryScheduler() {
    return taskScheduler("ws-auth-expiry-");
  }

  @Bean("wsHeartbeatExpiryScheduler")
  ThreadPoolTaskScheduler wsHeartbeatExpiryScheduler() {
    var scheduler = taskScheduler("ws-heartbeat-expiry-");
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  @Bean("wsHeartbeatScheduler")
  ThreadPoolTaskScheduler wsHeartbeatScheduler() {
    return taskScheduler("ws-heartbeat-");
  }

  private static ThreadPoolTaskScheduler taskScheduler(String threadNamePrefix) {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(threadNamePrefix);
    return scheduler;
  }
}
