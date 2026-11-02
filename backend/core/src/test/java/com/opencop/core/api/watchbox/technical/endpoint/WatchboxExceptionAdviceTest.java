// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

class WatchboxExceptionAdviceTest {

  private final WatchboxExceptionAdvice advice = new WatchboxExceptionAdvice();
  private Logger logger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(WatchboxExceptionAdvice.class);
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void unreadableBodyLogStaysBoundedEvenWhenDebugEnabled() {
    var ex =
        new HttpMessageNotReadableException(
            "bad enum value NORTH from request body",
            null,
            org.mockito.Mockito.mock(HttpInputMessage.class));

    advice.handleUnreadableBody(ex);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getFormattedMessage()).isEqualTo("Watchbox JSON parse error");
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage()).doesNotContain("NORTH", "request body", "bad enum");
  }

  @Test
  void validationResponsePreservesSourceNamesContext() {
    var exception =
        new WatchboxException(
            WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED,
            "Selected sources require object ID mapping correction",
            HttpStatus.CONFLICT,
            List.of("layerIds"),
            Map.of("sourceNames", List.of("Source A", "Source B")));

    var response = advice.handleWatchboxValidation(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().context())
        .containsEntry("sourceNames", List.of("Source A", "Source B"));
  }
}
