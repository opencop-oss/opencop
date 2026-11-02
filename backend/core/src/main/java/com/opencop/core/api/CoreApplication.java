// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api;

import com.opencop.core.api.common.AppConfig;
import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CoreApplication {

  static void main(String[] args) {
    createApplication().run(args);
  }

  static SpringApplication createApplication() {
    return SpringApplicationFactory.create(CoreApplication.class, AppConfig.class);
  }
}
