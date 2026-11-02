// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("unchecked")
class DataSourceTransportReaderTest {

  @Test
  void getRtspUrl_returnsEmpty_whenSourceNotFound() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var objectMapper = mock(ObjectMapper.class);
    when(jdbc.query(
            anyString(),
            any(Map.class),
            ArgumentMatchers.<RowMapper<Map.Entry<String, String>>>any()))
        .thenReturn(List.of());

    var reader = new DataSourceTransportReader(jdbc, objectMapper);

    assertThat(reader.getRtspUrl(UUID.randomUUID())).isEmpty();
  }

  @Test
  void getRtspUrl_returnsEmpty_whenProviderIsNotRtsp() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var objectMapper = mock(ObjectMapper.class);
    when(jdbc.query(
            anyString(),
            any(Map.class),
            ArgumentMatchers.<RowMapper<Map.Entry<String, String>>>any()))
        .thenReturn(List.of(Map.entry("TCP", "{}")));

    var reader = new DataSourceTransportReader(jdbc, objectMapper);

    assertThat(reader.getRtspUrl(UUID.randomUUID())).isEmpty();
  }

  @Test
  void getRtspUrl_excludesArchivedSources() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var objectMapper = mock(ObjectMapper.class);
    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    when(jdbc.query(
            sqlCaptor.capture(),
            any(Map.class),
            ArgumentMatchers.<RowMapper<Map.Entry<String, String>>>any()))
        .thenReturn(List.of());

    new DataSourceTransportReader(jdbc, objectMapper).getRtspUrl(UUID.randomUUID());

    assertThat(sqlCaptor.getValue()).containsIgnoringCase("archived_at IS NULL");
  }

  @Test
  void getRtspUrl_returnsEmpty_whenConnectionSpecFailsToParse() throws Exception {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var objectMapper = mock(ObjectMapper.class);
    when(jdbc.query(
            anyString(),
            any(Map.class),
            ArgumentMatchers.<RowMapper<Map.Entry<String, String>>>any()))
        .thenReturn(List.of(Map.entry("RTSP", "not-valid-json")));
    when(objectMapper.readValue(anyString(), any(Class.class)))
        .thenThrow(new RuntimeException("parse error"));

    var reader = new DataSourceTransportReader(jdbc, objectMapper);

    assertThat(reader.getRtspUrl(UUID.randomUUID())).isEmpty();
  }
}
