// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support;

import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.websocket.technical.gateway.websocket.WebSocketEventListener;
import com.opencop.websocket.technical.spring.config.AppConfig;
import com.opencop.websocket.technical.spring.config.CacheConfig;
import com.opencop.websocket.technical.spring.config.GeoCacheConfiguration;
import com.opencop.websocket.technical.spring.config.SecurityConfig;
import com.opencop.websocket.technical.spring.config.WebSocketConfig;
import com.opencop.websocket.technical.spring.config.WebSocketSchedulers;
import com.opencop.websocket.technical.spring.websocket.OCMPWebSocketHandler;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackageClasses = {
      OCMPWebSocketHandler.class,
      SessionExpiryKiller.class,
      WebSocketEventListener.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  AppConfig.class,
  CacheConfig.class,
  GeoCacheConfiguration.class,
  SecurityConfig.class,
  WebSocketConfig.class,
  WebSocketSchedulers.class,
  WebSocketComponentTestConfig.class
})
public class WebSocketComponentTestApplication {}
