// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect;

import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
class ConnectApplication {

  // To enable SSL debug logging for troubleshooting MTLS connections:
  // mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djavax.net.debug=ssl:handshake"
  static void main(String[] args) {
    createApplication().run(args);
  }

  static SpringApplication createApplication() {
    return SpringApplicationFactory.create(ConnectApplication.class);
  }
}
