// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalJobAdmissionScriptGatewayTest {

  @Test
  void constructor_loadsAdmissionScriptsFromResources() throws Exception {
    var gateway =
        new RedisHistoricalJobAdmissionScriptGateway(
            mock(StringRedisTemplate.class),
            HistoricalJobControlKeyLayout.STANDALONE,
            new HistoricalExecutionProperties(),
            new ProcessorHistoricalRedisScripts());

    assertScriptContains(gateway, "registerScript", "-- Script: admission_register");
    assertScriptContains(
        gateway, "recordExecutionExitScript", "-- Script: admission_record_execution_exit");
    assertScriptContains(gateway, "recordTerminalScript", "-- Script: admission_record_terminal");
    assertScriptContains(
        gateway, "cleanupAdmissionStateScript", "-- Script: admission_cleanup_state");
  }

  private static void assertScriptContains(
      RedisHistoricalJobAdmissionScriptGateway gateway, String fieldName, String expected)
      throws Exception {
    var field = RedisHistoricalJobAdmissionScriptGateway.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    Object value = field.get(gateway);
    assertTrue(value instanceof RedisScript<?>);
    assertTrue(((RedisScript<?>) value).getScriptAsString().contains(expected));
  }
}
