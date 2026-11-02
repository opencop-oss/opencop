// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.spring.endpoint;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;

import com.opencop.api.connect.business.connector.ConnectionLifecycleService;
import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.business.connector.DefaultConnectorListener;
import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler;
import com.opencop.api.connect.business.reconciliation.DesiredStateOutagePolicy;
import com.opencop.api.connect.business.usecase.event.RawBytesEventHandler;
import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder;
import com.opencop.api.connect.ports.gateway.observability.integration.IntegrationAvailabilityRecorder;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.api.connect.ports.usecase.event.EventHandler;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.config.ConnectionBehaviorRuntimeConfiguration;
import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.api.connect.technical.gateway.postgres.ConnectInstanceRegistrar;
import com.opencop.api.connect.technical.gateway.postgres.PostgresNudgeListenerConnectionSource;
import com.opencop.api.connect.technical.gateway.postgres.PostgresSocketNudgeListener;
import com.opencop.api.connect.technical.gateway.reconciliation.DesiredOwnershipReconciliationScheduler;
import com.opencop.api.connect.technical.gateway.redis.ConnectPushSubscriber;
import com.opencop.api.connect.technical.gateway.resolver.TcpFramingResolver;
import com.opencop.api.connect.technical.gateway.socket.ConnectionBehaviorRuntime;
import com.opencop.api.connect.technical.gateway.socket.ConnectorRegistry;
import com.opencop.api.connect.technical.gateway.socket.factory.rtsp.RtspKlvConnectorFactory;
import com.opencop.api.connect.technical.gateway.socket.factory.tcp.TcpConnectorFactory;
import com.opencop.api.connect.technical.gateway.socket.factory.udp.UdpConnectorFactory;
import com.opencop.api.connect.technical.observability.access.MicrometerAccessModeTransitionRecorder;
import com.opencop.api.connect.technical.observability.integration.MicrometerIntegrationAvailabilityRecorder;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;
import com.opencop.core.sdk.business.registry.integration.connection.behavior.ConnectionBehaviorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.helper.InstanceIdentity;
import com.opencop.core.sdk.model.push.PushMessage;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochReader;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.config.integration.connection.mapping.ConnectionMappingConfiguration;
import com.opencop.core.sdk.technical.gateway.api.PostgresConnectWorkerHealthRepository;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRestartEpochRepository;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRuntimeObservationRepository;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.time.Clock;
import java.util.HashMap;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.ObjectMapper;

@Configuration
// Connect hosts connection behavior and the connection settings it is planned from, the connection
// mapping that provides TCP framing, and their connection schema and identity prerequisites, but
// intentionally rejects ingress mapping and credential extraction. This policy is exhaustive: every
// catalog addition must be classified here before Connect can start.
@EnableIntegrationCapabilities(
    supported = {CONNECTION_BEHAVIOR, CONNECTION_SETTINGS, CONNECTION_MAPPING},
    rejected = {EGRESS_MAPPING, INGRESS_MAPPING, CONNECTION_INPUT, GEOCODING, CONNECTION_EXTENSION})
@ComponentScan({
  "com/opencop/core/sdk/technical/gateway/blob",
  "com/opencop/core/sdk/technical/exception",
  "com/opencop/core/sdk/technical/gateway/profile",
  "com/opencop/core/sdk/technical/gateway/secrets",
  "com/opencop/core/sdk/technical/gateway/broker"
})
@Import({ConnectionBehaviorRuntimeConfiguration.class, ConnectionMappingConfiguration.class})
public class AppConfig {

  public static final String RTSP_KLV_TRACER_BEAN = "rtspKlvTracer";

  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
  private static final long REDIS_SUBSCRIPTION_REGISTRATION_WAIT_MS = 10_000L;
  private static final long REDIS_SUBSCRIPTION_RECOVERY_INITIAL_MS = 1_000L;
  private static final long REDIS_SUBSCRIPTION_RECOVERY_MAX_MS = 30_000L;

  private final BaseKafkaConfig baseKafkaConfig;
  private final String instanceId = InstanceIdentity.resolveInstanceId("connect");

  public AppConfig(BaseKafkaConfig baseKafkaConfig) {
    this.baseKafkaConfig = baseKafkaConfig;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  {
    log.info("Connect instance ID: {}", instanceId);
  }

  @Bean(RTSP_KLV_TRACER_BEAN)
  Tracer rtspKlvTracer() {
    return GlobalOpenTelemetry.getTracer(RtspKlvObservability.TRACER_NAME);
  }

  @Bean
  RtspKlvObservability rtspKlvObservability(
      MeterRegistry meterRegistry, @Qualifier(RTSP_KLV_TRACER_BEAN) Tracer rtspKlvTracer) {
    return new RtspKlvObservability(meterRegistry, rtspKlvTracer);
  }

  @Bean
  RtspConnectorDefaults rtspConnectorDefaults(ConnectProperties connectProperties) {
    return connectProperties.getRtspKlv().connectorDefaults();
  }

  @Bean
  public ConnectionManager connectionManager(
      TcpConnectorFactory tcpConnectorFactory,
      UdpConnectorFactory udpConnectorFactory,
      RtspKlvConnectorFactory rtspKlvConnectorFactory,
      ConnectProperties connectProperties,
      SourceRuntimeObservationWriter sourceRuntimeObservationWriter,
      AccessModeTransitionRecorder accessModeTransitionRecorder,
      IntegrationAvailabilityRecorder integrationAvailabilityRecorder,
      ConnectionBehaviorRegistry behaviorRegistry,
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionBehaviorRuntime behaviorRuntime) {
    return new ConnectionManager(
        new ConnectorRegistry(
            tcpConnectorFactory,
            udpConnectorFactory,
            rtspKlvConnectorFactory,
            behaviorRegistry,
            settingsRegistry,
            behaviorRuntime),
        instanceId,
        connectProperties.getRuntime().getStatusTtl(),
        sourceRuntimeObservationWriter,
        accessModeTransitionRecorder,
        integrationAvailabilityRecorder);
  }

  @Bean
  AccessModeTransitionRecorder accessModeTransitionRecorder(MeterRegistry meterRegistry) {
    return new MicrometerAccessModeTransitionRecorder(meterRegistry);
  }

  @Bean
  IntegrationAvailabilityRecorder integrationAvailabilityRecorder(MeterRegistry meterRegistry) {
    return new MicrometerIntegrationAvailabilityRecorder(meterRegistry);
  }

  @Bean
  public ConnectorListener defaultListener(EventHandler<byte[]> eventHandler) {
    return new DefaultConnectorListener(eventHandler);
  }

  @Bean
  TcpFramingResolver tcpFramingResolver(
      ConnectionMappingRegistry mappingRegistry, ConnectProperties connectProperties) {
    return new TcpFramingResolver(mappingRegistry, connectProperties.getSocket());
  }

  @Bean
  EventHandler<byte[]> byteArrayEventHandler(MessageBrokerProducer<byte[]> messageBrokerProducer) {
    return new RawBytesEventHandler(messageBrokerProducer);
  }

  @Bean
  MessageBrokerProducer<byte[]> byteArrayMessageBrokerProducer(
      KafkaTemplate<String, byte[]> kafkaTemplate) {
    // TODO inject the topic from config
    return new KafkaProducer<>(kafkaTemplate, "opencop.event.external.raw");
  }

  @Bean
  ConnectWorkerHealthWriter connectWorkerHealthWriter(NamedParameterJdbcTemplate jdbc) {
    return new PostgresConnectWorkerHealthRepository(jdbc);
  }

  @Bean
  SourceRuntimeObservationWriter sourceRuntimeObservationWriter(NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRuntimeObservationRepository(jdbc);
  }

  @Bean
  SourceRestartEpochReader sourceRestartEpochReader(NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRestartEpochRepository(jdbc);
  }

  @Bean
  ConnectInstanceRegistrar connectInstanceRegistrar(
      ConnectionManager connectionManager, ConnectWorkerHealthWriter workerHealthWriter) {
    return new ConnectInstanceRegistrar(connectionManager, workerHealthWriter, instanceId);
  }

  @Bean
  ConnectionLifecycleService connectionLifecycleService(
      ConnectionManager connectionManager,
      DesiredSocketSourceRepository desiredSocketSourceRepository,
      ConnectorListener defaultListener) {
    return new ConnectionLifecycleService(
        connectionManager, desiredSocketSourceRepository, defaultListener, instanceId);
  }

  @Bean
  DesiredOwnershipReconciler desiredOwnershipReconciler(
      ConnectionManager connectionManager,
      DesiredSocketSourceRepository desiredSocketSourceRepository,
      ConnectionLifecycleService lifecycleService,
      SourceRestartEpochReader restartEpochReader,
      ConnectProperties connectProperties) {
    var desiredState = connectProperties.getDesiredState();
    return new DesiredOwnershipReconciler(
        connectionManager,
        desiredSocketSourceRepository,
        lifecycleService,
        instanceId,
        connectProperties.getReconciliation().getRecoveryMinInterval(),
        restartEpochReader,
        new DesiredStateOutagePolicy(
            desiredState.isStopOnUnavailable(), desiredState.getUnavailableGrace()));
  }

  @Bean
  DesiredOwnershipReconciliationScheduler desiredOwnershipReconciliationScheduler(
      DesiredOwnershipReconciler reconciler, ConnectProperties connectProperties) {
    return new DesiredOwnershipReconciliationScheduler(
        reconciler,
        connectProperties.getReconciliation(),
        connectProperties.getPeriodicDataSourceRestart());
  }

  @Bean(destroyMethod = "close")
  PostgresNudgeListenerConnectionSource postgresNudgeListenerConnectionSource(
      DataSourceProperties dataSourceProperties, ConnectProperties connectProperties) {
    return new PostgresNudgeListenerConnectionSource(
        dataSourceProperties, connectProperties.getPostgresListener().getDataSource());
  }

  // Listener can be disabled at runtime via opencop.connect.nudge.enabled=false. When disabled,
  // Connect ignores PG NOTIFY events and relies entirely on the periodic reconciler tick. Useful as
  // an ops kill-switch if LISTEN/NOTIFY misbehaves in production, and as a test lever to validate
  // that the system self-heals without the fast-path nudge.
  @Bean
  @ConditionalOnProperty(
      name = "opencop.connect.nudge.enabled",
      havingValue = "true",
      matchIfMissing = true)
  PostgresSocketNudgeListener postgresSocketNudgeListener(
      PostgresNudgeListenerConnectionSource connectionSource,
      ObjectMapper objectMapper,
      DesiredOwnershipReconciler reconciler,
      ConnectProperties connectProperties) {
    return new PostgresSocketNudgeListener(
        connectionSource,
        objectMapper,
        reconciler,
        connectProperties.getPostgresListener().getShutdown().getJoinTimeout());
  }

  @Bean
  ConnectPushSubscriber connectPushSubscriber(
      ConnectionManager connectionManager, ObjectMapper objectMapper) {
    return new ConnectPushSubscriber(connectionManager, objectMapper);
  }

  /* TODO: I'm assuming we'll want a more generic "ConnectMessage" that each "ConnectMessageListener" is tied to.
  At that point we would do listener.channelFor(instanceId). */
  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, ConnectPushSubscriber pushSubscriber) {
    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setMaxSubscriptionRegistrationWaitingTime(REDIS_SUBSCRIPTION_REGISTRATION_WAIT_MS);
    container.setRecoveryBackoff(redisSubscriptionRecoveryBackoff());
    container.setErrorHandler(
        t -> log.warn("Redis pub/sub listener error; subscription recovery will retry", t));
    container.addMessageListener(
        pushSubscriber, new ChannelTopic(PushMessage.channelFor(instanceId)));
    return container;
  }

  private static ExponentialBackOff redisSubscriptionRecoveryBackoff() {
    var backOff =
        new ExponentialBackOff(
            REDIS_SUBSCRIPTION_RECOVERY_INITIAL_MS, ExponentialBackOff.DEFAULT_MULTIPLIER);
    backOff.setMaxInterval(REDIS_SUBSCRIPTION_RECOVERY_MAX_MS);
    return backOff;
  }

  @Bean
  @Primary
  KafkaTemplate<String, byte[]> rawBytesTemplate() {
    var props = new HashMap<String, Object>();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  SecurityFilterChain connectSecurityChain(HttpSecurity http) {
    http.authorizeHttpRequests(
            auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().denyAll())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .oauth2Login(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);

    return http.build();
  }
}
