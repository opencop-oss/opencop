// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

class ProductionLogbackConfigurationTest {

  @Test
  void consoleEncoderRendersArbitraryStructuredContext() throws Exception {
    var context = configuredContext();
    try {
      var encoded =
          encode(
              context,
              Level.INFO,
              "Structured application event",
              new KeyValuePair("correlationId", "request-42"),
              new KeyValuePair("attempt", 3));

      assertThat(encoded)
          .contains("Structured application event")
          .contains("correlationId=\"request-42\"")
          .contains("attempt=\"3\"");
    } finally {
      context.stop();
    }
  }

  @Test
  void consoleEncoderSupportsIncompatibleAutoStartFieldContract() throws Exception {
    var context = configuredContext();
    try {
      var encoded =
          encode(
              context,
              Level.WARN,
              "Skipping auto-start for incompatible socket access mode",
              new KeyValuePair("source", "source-id"),
              new KeyValuePair("transport", "UDP"),
              new KeyValuePair("mode", "RW"),
              new KeyValuePair("cause", "incompatible_transport_access"));

      assertThat(encoded)
          .contains("source=\"source-id\"")
          .contains("transport=\"UDP\"")
          .contains("mode=\"RW\"")
          .contains("cause=\"incompatible_transport_access\"");
    } finally {
      context.stop();
    }
  }

  private static LoggerContext configuredContext() throws Exception {
    var context = new LoggerContext();
    var configurator = new JoranConfigurator();
    configurator.setContext(context);
    var configuration =
        ProductionLogbackConfigurationTest.class.getClassLoader().getResource("logback-spring.xml");
    configurator.doConfigure(configuration);
    return context;
  }

  private static String encode(
      LoggerContext context, Level level, String message, KeyValuePair... contextFields) {
    var event =
        new LoggingEvent(
            ProductionLogbackConfigurationTest.class.getName(),
            context.getLogger(ProductionLogbackConfigurationTest.class),
            level,
            message,
            null,
            null);
    for (var contextField : contextFields) {
      event.addKeyValuePair(contextField);
    }

    var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    var appender = assertInstanceOf(OutputStreamAppender.class, root.getAppender("CONSOLE"));
    var encoder = assertInstanceOf(PatternLayoutEncoder.class, appender.getEncoder());
    return new String(encoder.encode(event), StandardCharsets.UTF_8);
  }
}
