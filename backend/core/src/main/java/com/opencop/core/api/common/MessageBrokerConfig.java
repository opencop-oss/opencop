// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import com.opencop.core.api.event.business.policy.LayerBroadcastPolicy;
import com.opencop.core.api.event.business.policy.SourceBroadcastPolicy;
import com.opencop.core.api.event.business.usecase.event.BroadcastEventPublisher;
import com.opencop.core.api.event.business.usecase.event.LayerBroadcastEventHandler;
import com.opencop.core.api.event.business.usecase.event.SourceBroadcastEventHandler;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.geojson.Feature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class MessageBrokerConfig {

  private final BaseKafkaConfig baseKafkaConfig;

  public MessageBrokerConfig(BaseKafkaConfig baseKafkaConfig) {
    this.baseKafkaConfig = baseKafkaConfig;
  }

  @Bean
  public static BroadcastEventPublisher broadcastEventPublisher(
      final KafkaTemplate<String, Feature> kafkaTemplate,
      final UserManager<InternalUser> userManager,
      final PostGisService service) {
    return new BroadcastEventPublisher(
        new KafkaProducer<>(kafkaTemplate, "opencop.geo.mapped"), userManager, service);
  }

  @Bean
  public static LayerBroadcastEventHandler layerBroadcastEventHandler(
      final BroadcastEventPublisher publisher,
      final LayerBroadcastPolicy authorizationPolicy,
      final ResourceLifecycleGuard resourceLifecycleGuard,
      final JobPublisher jobPublisher) {
    return new LayerBroadcastEventHandler(
        publisher, authorizationPolicy, resourceLifecycleGuard, jobPublisher);
  }

  @Bean
  public static SourceBroadcastEventHandler sourceBroadcastEventHandler(
      final BroadcastEventPublisher publisher,
      final SourceBroadcastPolicy authorizationPolicy,
      final ResourceLifecycleGuard resourceLifecycleGuard,
      final JobPublisher jobPublisher) {
    return new SourceBroadcastEventHandler(
        publisher, authorizationPolicy, resourceLifecycleGuard, jobPublisher);
  }

  @Bean
  public KafkaTemplate<String, Feature> geoJsonWrapperKafkaTemplate() {
    return new KafkaTemplate<>(geoJsonWrapperProducerFactory());
  }

  private Map<String, Object> commonProps() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put("spring.json.use.type.headers", true);
    return props;
  }

  private ProducerFactory<String, Feature> geoJsonWrapperProducerFactory() {
    Map<String, Object> props = new HashMap<>(commonProps());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaGeoJsonSerializer.class);
    props.putAll(baseKafkaConfig.props());
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean("notificationProducer")
  MessageBrokerProducer<OpencopNotification> notificationProducer(
      KafkaTemplate<String, OpencopNotification> kafkaTemplate) {
    return new KafkaProducer<>(kafkaTemplate, "opencop.notification");
  }

  @Bean("rawEventProducer")
  MessageBrokerProducer<byte[]> rawEventProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
    return new KafkaProducer<>(kafkaTemplate, "opencop.event.external.raw");
  }

  @Bean
  public KafkaTemplate<String, byte[]> byteArrayWrapperKafkaTemplate() {
    return new KafkaTemplate<>(byteArrayWrapperProducerFactory());
  }

  private ProducerFactory<String, byte[]> byteArrayWrapperProducerFactory() {
    Map<String, Object> props = new HashMap<>(commonProps());

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.putAll(baseKafkaConfig.props());

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  KafkaTemplate<String, OpencopNotification> notificationKafkaTemplate() {
    var templateProps = new HashMap<String, Object>();

    templateProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    templateProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
    templateProps.putAll(baseKafkaConfig.props());

    var producerFactory =
        new DefaultKafkaProducerFactory<String, OpencopNotification>(templateProps);
    producerFactory.setValueSerializerSupplier(
        () ->
            new JacksonJsonSerializer<OpencopNotification>(
                JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build()));

    return new KafkaTemplate<>(producerFactory);
  }
}
