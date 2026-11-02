// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import com.opencop.api.connect.technical.gateway.socket.ConnectionBehaviorRuntime;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-owned resources for executing compiled connection behavior plans. */
@Configuration(proxyBeanMethods = false)
public class ConnectionBehaviorRuntimeConfiguration {

  static final String TIMING_SCHEDULER_BEAN = "connectionBehaviorTimingScheduler";
  static final String BEHAVIOR_EXECUTOR_BEAN = "connectionBehaviorExecutor";

  @Bean(name = TIMING_SCHEDULER_BEAN, destroyMethod = "shutdownNow")
  ScheduledThreadPoolExecutor connectionBehaviorTimingScheduler() {
    var scheduler =
        new ScheduledThreadPoolExecutor(
            1, Thread.ofPlatform().name("connection-behavior-timer-", 0).factory());
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  @Bean(name = BEHAVIOR_EXECUTOR_BEAN, destroyMethod = "shutdownNow")
  ExecutorService connectionBehaviorExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  ConnectionBehaviorRuntime connectionBehaviorRuntime(
      @Qualifier(TIMING_SCHEDULER_BEAN) ScheduledThreadPoolExecutor scheduler,
      @Qualifier(BEHAVIOR_EXECUTOR_BEAN) ExecutorService behaviorExecutor,
      Clock clock) {
    return new ConnectionBehaviorRuntime(scheduler, behaviorExecutor, clock);
  }
}
