// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.media.JpaEventAttachmentRepository;
import com.opencop.core.sdk.technical.gateway.db.media.JpaMediaUrlRepository;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.media.MediaUrlEntity;
import com.opencop.core.sdk.technical.gateway.db.media.MediaUrlJpaRepository;
import com.opencop.processor.business.usecase.event.EventAutoAttachmentService;
import com.opencop.processor.component.support.ProcessorComponentTestDatabaseConfig;
import com.opencop.processor.ports.gateway.db.DataSourceTransportGateway;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    classes = {
      EventAutoAttachmentServiceIT.TestApplication.class,
      EventAutoAttachmentServiceIT.TestContext.class
    })
class EventAutoAttachmentServiceIT extends AbstractDatabaseIT {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String OBJECT_ID = "track-42";
  private static final String RTSP_URL = "rtsp://camera.test/stream";

  @Autowired private EventAutoAttachmentService service;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE media_attachment CASCADE");
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.execute("TRUNCATE TABLE media_attachment CASCADE");
  }

  @Test
  void attachFromRecord_createsAllThreeRows_forRtspSource() {
    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    assertThat(countRows("SELECT COUNT(*) FROM media_attachment")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM media_url")).isEqualTo(1);
    assertThat(
            countRows(
                "SELECT COUNT(*) FROM event_attachment WHERE source_id = ? AND object_id = ?",
                SOURCE_ID,
                OBJECT_ID))
        .isEqualTo(1);
  }

  @Test
  void attachFromRecord_isIdempotent_doesNotDuplicateRows() {
    service.attachFromRecord(SOURCE_ID, OBJECT_ID);
    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    assertThat(countRows("SELECT COUNT(*) FROM media_attachment")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM event_attachment WHERE source_id = ?", SOURCE_ID))
        .isEqualTo(1);
  }

  private int countRows(String sql, Object... args) {
    var result = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return result != null ? result : 0;
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
      })
  @EntityScan(
      basePackageClasses = {
        MediaAttachmentEntity.class,
        MediaUrlEntity.class,
        EventAttachmentEntity.class
      })
  @EnableJpaRepositories(
      basePackageClasses = {
        MediaAttachmentJpaRepository.class,
        MediaUrlJpaRepository.class,
        EventAttachmentJpaRepository.class
      })
  @Import({
    JpaMediaUrlRepository.class,
    JpaEventAttachmentRepository.class,
    ProcessorComponentTestDatabaseConfig.class
  })
  static class TestApplication {}

  @TestConfiguration
  static class TestContext {

    @Bean
    DataSourceTransportGateway dataSourceTransportGateway() {
      var gateway = mock(DataSourceTransportGateway.class);
      when(gateway.getRtspUrl(any())).thenReturn(Optional.of(RTSP_URL));
      return gateway;
    }

    @Bean
    EventAutoAttachmentService eventAutoAttachmentService(
        DataSourceTransportGateway gateway,
        MediaUrlRepository mediaUrlRepository,
        EventAttachmentRepository eventAttachmentRepository) {
      return new EventAutoAttachmentService(
          gateway,
          mediaUrlRepository,
          eventAttachmentRepository,
          Caffeine.newBuilder().build(),
          Caffeine.newBuilder().build());
    }
  }
}
