// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.processor.component.support.AbstractProcessorKafkaDatabaseIT;
import com.opencop.processor.component.support.DeadLetterJpaComponentTestConfig;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.broker.MessageBrokerConfig;
import com.opencop.processor.technical.spring.config.broker.WatchboxRetainedRetryTracker;
import com.opencop.processor.technical.spring.consumer.NotificationConsumer;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import com.opencop.processor.technical.spring.dlt.DeadLetterRecord;
import com.opencop.processor.technical.spring.dlt.FixedDeadLetterConsumer;
import com.opencop.processor.technical.spring.dlt.ProcessorDeadLetterRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      FixedDeadLetterConsumerComponentIT.FixedDltTestContext.class
    },
    properties = "opencop.messaging-broker.dlt-indexing.enabled=true")
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "opencop.event.external.raw.dlt",
      "opencop.geo.mapped.dlt",
      "opencop.geo.validated.dlt",
      "opencop.notification",
      "opencop.notification.dlt",
      "opencop.jobs.dlt",
      "opencop.history.jobs.dlt"
    })
class FixedDeadLetterConsumerComponentIT extends AbstractProcessorKafkaDatabaseIT {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(25);
  private static final Duration QUIET_PERIOD = Duration.ofMillis(500);
  private static final String NOTIFICATION_TOPIC = "opencop.notification";
  private static final String NOTIFICATION_DLT_TOPIC = NOTIFICATION_TOPIC + ".dlt";
  private static final String VALIDATED_TOPIC = "opencop.geo.validated";
  private static final String VALIDATED_DLT_TOPIC = VALIDATED_TOPIC + ".dlt";
  private static final String RAW_DLT_TOPIC = "opencop.event.external.raw.dlt";
  private static final String MAPPED_DLT_TOPIC = "opencop.geo.mapped.dlt";
  private static final String JOBS_DLT_TOPIC = "opencop.jobs.dlt";
  private static final String HISTORY_DLT_TOPIC = "opencop.history.jobs.dlt";
  private static final String DATABASE_NAME = "fixed_dlt_index_component_it";
  private static final int DLT_PARTITION = 0;
  private static final int MAX_JSON_BYTES = 16 * 1024;
  private static final int MAX_EXCEPTION_MESSAGE_CHARS = 4 * 1024;

  @Autowired private ApplicationContext applicationContext;
  @Autowired private BlobClient blobClient;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private ProcessorDeadLetterRecordRepository repository;

  @BeforeEach
  void setUp() {
    jdbc.getJdbcTemplate().execute("TRUNCATE TABLE processor.processor_dead_letter_record CASCADE");
    reset(blobClient);
    awaitNotificationConsumerAssignment();
    awaitDltIndexConsumerAssignments();
  }

  @Test
  void nonAllowlistedFixedDltTopicIsNotIndexed() throws Exception {
    var record =
        KafkaTestRecords.byteArrayRecord(
            NOTIFICATION_DLT_TOPIC,
            "notification-dlt-" + UUID.randomUUID(),
            "{not-json".getBytes(StandardCharsets.UTF_8),
            Map.of(
                DeadLetterHeaders.STAGE,
                "NOTIFICATION",
                DeadLetterHeaders.FAILURE_KIND,
                "PROCESSING",
                DLT_ORIGINAL_TOPIC,
                NOTIFICATION_TOPIC,
                DLT_EXCEPTION_FQCN,
                "tools.jackson.core.exc.StreamReadException",
                DLT_EXCEPTION_MESSAGE,
                "bad notification"));

    kafkaTemplate.send(record).get();
    kafkaTemplate.flush();

    awaitStableFailureCount(0L);
    assertThat(repository.findByDltCoordinates(NOTIFICATION_DLT_TOPIC, DLT_PARTITION, 0L))
        .isEmpty();
  }

  @Test
  void fixedDltMessageWithSpringHeadersIndexesSourceAndDltCoordinates() throws Exception {
    var sendResult = kafkaTemplate.send(springDltMessage("headered-dlt")).get();
    var offset = sendResult.getRecordMetadata().offset();

    var failure = awaitFailureByDltCoordinates(VALIDATED_DLT_TOPIC, DLT_PARTITION, offset);
    assertThat(failure.stage()).isEqualTo(DeadLetterRecord.Stage.REAL_TIME);
    assertThat(failure.originalTopic()).isEqualTo(VALIDATED_TOPIC);
    assertThat(failure.originalPartition()).isEqualTo(0);
    assertThat(failure.originalOffset()).isEqualTo(41L);
    assertThat(failure.dltTopic()).isEqualTo(VALIDATED_DLT_TOPIC);
    assertThat(failure.dltPartition()).isEqualTo(DLT_PARTITION);
    assertThat(failure.dltOffset()).isEqualTo(offset);
    assertThat(failure.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
    assertThat(failure.exceptionMessage()).isEqualTo("bad feature");
  }

  @Test
  void fixedDltMessageWithMalformedAndOversizedHeadersPersistsBoundedMetadata() throws Exception {
    var payload = "hostile-payload".getBytes(StandardCharsets.UTF_8);
    var sendResult = kafkaTemplate.send(adversarialDltMessage("adversarial-dlt", payload)).get();
    var offset = sendResult.getRecordMetadata().offset();

    var failure = awaitFailureByDltCoordinates(VALIDATED_DLT_TOPIC, DLT_PARTITION, offset);
    assertThat(failure.stage()).isEqualTo(DeadLetterRecord.Stage.UNKNOWN);
    assertThat(failure.failureKind()).isEqualTo(DeadLetterRecord.FailureKind.UNKNOWN);
    assertThat(failure.originalTopic()).isEqualTo("UNKNOWN");
    assertThat(failure.originalPartition()).isNull();
    assertThat(failure.originalOffset()).isNull();
    assertThat(failure.exceptionClass()).isEqualTo("UNKNOWN");
    assertThat(failure.exceptionMessage()).hasSize(MAX_EXCEPTION_MESSAGE_CHARS);
    assertThat(failure.contextJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
    assertThat(failure.headersJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
    assertThat(failure.payloadRef())
        .isEqualTo("kafka://%s/%d/%d".formatted(VALIDATED_DLT_TOPIC, DLT_PARTITION, offset));
    assertThat(failure.payloadExpiresAt()).isNotNull();
    assertThat(failure.payloadSha256()).isNull();
    assertThat(failure.payloadSizeBytes()).isNull();
    verify(blobClient, never()).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void duplicateFixedDltDeliveryKeepsOneRepositoryRow() throws Exception {
    var sendResult = kafkaTemplate.send(springDltMessage("duplicate-dlt")).get();
    var offset = sendResult.getRecordMetadata().offset();

    awaitFailureByDltCoordinates(VALIDATED_DLT_TOPIC, DLT_PARTITION, offset);
    awaitStableFailureCount(1L);

    assertThat(repository.count()).isEqualTo(1L);
  }

  @Test
  void allowlistedFixedDltTopicsHaveIndexContainers() {
    var dltIndexContainers =
        applicationContext.getBeansOfType(MessageListenerContainer.class).entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("DltIndexContainer"))
            .map(Map.Entry::getValue)
            .toList();
    var containerTopics =
        dltIndexContainers.stream()
            .flatMap(
                container ->
                    container.getContainerProperties().getTopics() == null
                        ? Stream.empty()
                        : Arrays.stream(container.getContainerProperties().getTopics()))
            .filter(topic -> topic.endsWith(".dlt"))
            .toList();

    assertThat(dltIndexContainers)
        .allSatisfy(
            container -> {
              assertThat(
                      ((AbstractMessageListenerContainer<?, ?>) container).getCommonErrorHandler())
                  .isInstanceOf(DefaultErrorHandler.class);
              assertThat(container.getContainerProperties().getAckMode())
                  .isEqualTo(ContainerProperties.AckMode.MANUAL);
              assertThat(container.getContainerProperties().getMessageListener())
                  .isInstanceOf(BatchAcknowledgingMessageListener.class);
            });
    assertThat(containerTopics).contains(RAW_DLT_TOPIC, VALIDATED_DLT_TOPIC);
    assertThat(containerTopics)
        .doesNotContain(
            MAPPED_DLT_TOPIC, NOTIFICATION_DLT_TOPIC, JOBS_DLT_TOPIC, HISTORY_DLT_TOPIC);
    assertThat(containerTopics).containsOnlyOnce(VALIDATED_DLT_TOPIC);
  }

  private void awaitNotificationConsumerAssignment() {
    MessageListenerContainer listenerContainer =
        kafkaListenerEndpointRegistry.getListenerContainer("notificationConsumer");
    assertThat(listenerContainer).isNotNull();
    ContainerTestUtils.waitForAssignment(
        listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
  }

  private void awaitDltIndexConsumerAssignments() {
    applicationContext
        .getBeansOfType(MessageListenerContainer.class)
        .forEach(
            (beanName, listenerContainer) -> {
              if (beanName.endsWith("DltIndexContainer")) {
                ContainerTestUtils.waitForAssignment(listenerContainer, 1);
              }
            });
  }

  private DeadLetterRecord awaitFailureByDltCoordinates(
      String dltTopic, int dltPartition, long dltOffset) throws InterruptedException {
    return awaitFailure(
        () -> repository.findByDltCoordinates(dltTopic, dltPartition, dltOffset).orElse(null));
  }

  private DeadLetterRecord awaitFailure(FailureSupplier supplier) throws InterruptedException {
    long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
    DeadLetterRecord failure = supplier.get();
    while (failure == null && System.nanoTime() < deadline) {
      Thread.sleep(100L);
      failure = supplier.get();
    }
    assertThat(failure).isNotNull();
    return failure;
  }

  private void awaitStableFailureCount(long expectedCount) throws InterruptedException {
    long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
    long stableSince = -1L;
    while (System.nanoTime() < deadline) {
      if (repository.count() == expectedCount) {
        if (stableSince < 0L) {
          stableSince = System.nanoTime();
        }
        if (System.nanoTime() - stableSince >= QUIET_PERIOD.toNanos()) {
          return;
        }
      } else {
        stableSince = -1L;
      }
      Thread.sleep(100L);
    }
    assertThat(repository.count()).isEqualTo(expectedCount);
  }

  private static ProducerRecord<String, byte[]> springDltMessage(String key) {
    return KafkaTestRecords.byteArrayRecord(
        VALIDATED_DLT_TOPIC,
        key,
        "failed-feature".getBytes(StandardCharsets.UTF_8),
        Map.of(
            DeadLetterHeaders.STAGE,
            "REAL_TIME",
            DeadLetterHeaders.FAILURE_KIND,
            "PROCESSING",
            DLT_ORIGINAL_TOPIC,
            VALIDATED_TOPIC,
            DLT_ORIGINAL_PARTITION,
            "0",
            DLT_ORIGINAL_OFFSET,
            "41",
            DLT_EXCEPTION_FQCN,
            "java.lang.IllegalStateException",
            DLT_EXCEPTION_MESSAGE,
            "bad feature"));
  }

  private static ProducerRecord<String, byte[]> adversarialDltMessage(String key, byte[] payload) {
    return KafkaTestRecords.byteArrayRecord(
        VALIDATED_DLT_TOPIC,
        key,
        payload,
        Map.of(
            DeadLetterHeaders.STAGE,
            "NOT_A_STAGE",
            DeadLetterHeaders.FAILURE_KIND,
            "NOT_A_KIND",
            DLT_ORIGINAL_PARTITION,
            "not-an-int",
            DLT_ORIGINAL_OFFSET,
            "not-a-long",
            DLT_EXCEPTION_FQCN,
            " ",
            DLT_EXCEPTION_MESSAGE,
            "x".repeat(MAX_EXCEPTION_MESSAGE_CHARS + 128),
            DeadLetterFailureIndexer.CONTEXT_HEADER,
            "c".repeat(MAX_JSON_BYTES + 128),
            "oversized-header",
            "h".repeat(MAX_JSON_BYTES + 128)));
  }

  @FunctionalInterface
  private interface FailureSupplier {
    DeadLetterRecord get() throws InterruptedException;
  }

  @TestConfiguration
  @Import({
    MessageBrokerConfig.class,
    NotificationConsumer.class,
    FixedDeadLetterConsumer.class,
    DeadLetterIndexingErrorHandlerFactory.class,
    ProcessorDeadLetterRecordRepository.class,
    DeadLetterJpaComponentTestConfig.class
  })
  static class FixedDltTestContext {

    @Bean
    BaseKafkaConfig baseKafkaConfig(EmbeddedKafkaBroker embeddedKafkaBroker) {
      return new BaseKafkaConfig(
          Map.of("bootstrap.servers", embeddedKafkaBroker.getBrokersAsString()));
    }

    @Bean
    BlobClient blobClient() {
      return mock(BlobClient.class);
    }

    @Bean
    DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password,
        @Value("${opencop.db.driver}") String driverClassName)
        throws SQLException {
      String databaseUrl = isolatedDatabaseUrl(url, username, password);
      var dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName(driverClassName);
      dataSource.setUrl(databaseUrl);
      dataSource.setUsername(username);
      dataSource.setPassword(password);
      return dataSource;
    }

    @Bean
    InitializingBean fixedDltFlywayInitializer(DataSource dataSource) {
      return () ->
          Flyway.configure()
              .dataSource(dataSource)
              .baselineOnMigrate(true)
              .locations("filesystem:" + sdkMigrationPath())
              .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
              .load()
              .migrate();
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    DeadLetterFailureIndexer deadLetterFailureIndexer(
        ProcessorDeadLetterRecordRepository repository,
        ObjectMapper objectMapper,
        DeadLetterObservability observability,
        KafkaConsumersProperties kafkaConsumersProperties) {
      kafkaConsumersProperties.getDltIndexing().setPayloadRetention(Duration.ofDays(7));
      return new DeadLetterFailureIndexer(
          repository, objectMapper, observability, kafkaConsumersProperties);
    }

    @Bean
    NotificationHandler<?> notificationHandler() {
      return mock(NotificationHandler.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ProcessorMetrics opencopProcessorMetrics(MeterRegistry meterRegistry) {
      return new ProcessorMetrics(meterRegistry);
    }

    /**
     * {@link MessageBrokerConfig} requires the tracker so production fails fast if the watchbox
     * liveness wiring goes missing; this context runs no watchbox listener, so the tracker is inert
     * here.
     */
    @Bean
    WatchboxRetainedRetryTracker watchboxRetainedRetryTracker(ProcessorMetrics metrics) {
      return new WatchboxRetainedRetryTracker("opencop.geo.validated", Clock.systemUTC(), metrics);
    }

    @Bean
    DeadLetterObservability deadLetterObservability(ProcessorMetrics metrics) {
      return new DeadLetterObservability(metrics);
    }

    private static String isolatedDatabaseUrl(String url, String username, String password)
        throws SQLException {
      try (var connection = DriverManager.getConnection(url, username, password);
          var statement = connection.createStatement()) {
        try {
          statement.execute("CREATE DATABASE " + DATABASE_NAME);
        } catch (SQLException e) {
          if (!"42P04".equals(e.getSQLState())) {
            throw e;
          }
        }
      }
      int databaseNameStart = url.lastIndexOf('/') + 1;
      return url.substring(0, databaseNameStart) + DATABASE_NAME;
    }

    private static String sdkMigrationPath() {
      var processorRelative = Path.of("..", "sdk", "src", "main", "resources", "db", "migration");
      if (Files.isDirectory(processorRelative)) {
        return processorRelative.toAbsolutePath().normalize().toString();
      }
      return Path.of("sdk", "src", "main", "resources", "db", "migration")
          .toAbsolutePath()
          .normalize()
          .toString();
    }
  }
}
