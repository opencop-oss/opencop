// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical;

import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebSocketApplication {

  static void main(String[] args) {
    createApplication().run(args);
  }

  static SpringApplication createApplication() {
    return SpringApplicationFactory.create(WebSocketApplication.class);
  }
}
