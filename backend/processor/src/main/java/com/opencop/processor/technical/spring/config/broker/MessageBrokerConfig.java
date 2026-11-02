// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;

import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.AcknowledgingKafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.CoordinatedProducerFactoryResetter;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaConfigHelper;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProducerFactoryResetter;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonSerializer;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.FixedDeadLetterConsumer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.geojson.Feature;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import tools.jackson.databind.ObjectMapper;

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaConsumersProperties.class)
public class MessageBrokerConfig implements BeanFactoryAware, InitializingBean {

  private final KafkaConsumersProperties consumersProperties;
  private ConfigurableListableBeanFactory beanFactory;
  private final BaseKafkaConfig baseKafkaConfig;
  private final ObjectProvider<JobStatusNotificationPublisher> jobStatusNotificationPublisher;
  private final ObjectProvider<FixedDeadLetterConsumer> fixedDeadLetterConsumer;
  private final DeadLetterIndexingErrorHandlerFactory dltIndexingErrorHandlerFactory;
  private final ObjectMapper objectMapper;
  private final KafkaListenerFactoryBuilder kafkaListenerFactoryBuilder;
  private final WatchboxRetainedRetryTracker watchboxRetainedRetryTracker;
  private static final String DLT_CONTAINER_SUFFIX = "DltIndexContainer";

  public MessageBrokerConfig(
      KafkaConsumersProperties consumersProperties,
      BaseKafkaConfig config,
      ObjectProvider<JobStatusNotificationPublisher> jobStatusNotificationPublisher,
      ObjectProvider<FixedDeadLetterConsumer> fixedDeadLetterConsumer,
      DeadLetterIndexingErrorHandlerFactory dltIndexingErrorHandlerFactory,
      ObjectMapper objectMapper,
      ProcessorMetrics processorMetrics,
      WatchboxRetainedRetryTracker watchboxRetainedRetryTracker) {
    this.consumersProperties = consumersProperties;
    this.baseKafkaConfig = config;
    this.jobStatusNotificationPublisher = jobStatusNotificationPublisher;
    this.fixedDeadLetterConsumer = fixedDeadLetterConsumer;
    this.dltIndexingErrorHandlerFactory = dltIndexingErrorHandlerFactory;
    this.objectMapper = objectMapper;
    this.watchboxRetainedRetryTracker = watchboxRetainedRetryTracker;
    this.kafkaListenerFactoryBuilder =
        new KafkaListenerFactoryBuilder(processorMetrics, watchboxRetainedRetryTracker);
  }

  @Bean("userNotificationProducer")
  MessageBrokerProducer<byte[]> userNotificationProducer(
      @Value("${opencop.messaging-broker.listeners.notification.topic}") String topic,
      @Qualifier("notificationKafkaTemplate") KafkaTemplate<String, byte[]> kafkaTemplate) {
    return new KafkaProducer<>(kafkaTemplate, topic);
  }

  @Bean("validatedProducer")
  MessageBrokerProducer<Feature> validatedProducer(
      @Value("${opencop.messaging-broker.listeners.realTime.topic}") String topic,
      @Qualifier("acknowledgedFeatureKafkaTemplate") KafkaTemplate<String, Feature> kafkaTemplate,
      @Qualifier("acknowledgedFeatureProducerFactoryResetter")
          ProducerFactoryResetter producerFactoryResetter) {
    return new AcknowledgingKafkaProducer<>(
        topic, kafkaTemplate, consumersProperties.getProducerAckTimeout(), producerFactoryResetter);
  }

  @Bean("mappedProducer")
  MessageBrokerProducer<Feature> mappedProducer(
      @Value("${opencop.messaging-broker.listeners.validation.topic}") String topic,
      @Qualifier("acknowledgedFeatureKafkaTemplate") KafkaTemplate<String, Feature> kafkaTemplate,
      @Qualifier("acknowledgedFeatureProducerFactoryResetter")
          ProducerFactoryResetter producerFactoryResetter) {
    return new AcknowledgingKafkaProducer<>(
        topic, kafkaTemplate, consumersProperties.getProducerAckTimeout(), producerFactoryResetter);
  }

  @Bean("rawDltProducer")
  MessageBrokerProducer<byte[]> rawDltProducer(
      @Value("${opencop.messaging-broker.listeners.raw.topic}") String rawTopic,
      @Qualifier("acknowledgedByteArrayKafkaTemplate") KafkaTemplate<String, byte[]> kafkaTemplate,
      @Qualifier("acknowledgedByteArrayProducerFactoryResetter")
          ProducerFactoryResetter producerFactoryResetter) {
    return new AcknowledgingKafkaProducer<>(
        rawTopic + DLT_SUFFIX,
        kafkaTemplate,
        consumersProperties.getProducerAckTimeout(),
        producerFactoryResetter);
  }

  @Bean("notificationKafkaTemplate")
  KafkaTemplate<String, byte[]> notificationKafkaTemplate() {
    var props = new HashMap<String, Object>();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @SuppressWarnings(
      "java:S4144") // Separate beans keep notification and job producer factories isolated.
  @Bean("jobKafkaTemplate")
  KafkaTemplate<String, byte[]> jobKafkaTemplate() {
    var props = new HashMap<String, Object>();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean("acknowledgedFeatureProducerFactory")
  DefaultKafkaProducerFactory<String, Feature> acknowledgedFeatureProducerFactory() {
    return new DefaultKafkaProducerFactory<>(
        acknowledgedProducerProps(KafkaGeoJsonSerializer.class));
  }

  @Bean("acknowledgedFeatureKafkaTemplate")
  KafkaTemplate<String, Feature> acknowledgedFeatureKafkaTemplate(
      @Qualifier("acknowledgedFeatureProducerFactory")
          DefaultKafkaProducerFactory<String, Feature> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean("acknowledgedFeatureProducerFactoryResetter")
  ProducerFactoryResetter acknowledgedFeatureProducerFactoryResetter(
      @Qualifier("acknowledgedFeatureProducerFactory")
          DefaultKafkaProducerFactory<String, Feature> producerFactory) {
    return new CoordinatedProducerFactoryResetter(producerFactory::reset);
  }

  @Bean("acknowledgedByteArrayProducerFactory")
  DefaultKafkaProducerFactory<String, byte[]> acknowledgedByteArrayProducerFactory() {
    return new DefaultKafkaProducerFactory<>(acknowledgedProducerProps(ByteArraySerializer.class));
  }

  private Map<String, Object> acknowledgedProducerProps(Class<?> valueSerializer) {
    var props = new HashMap<>(baseKafkaConfig.props());
    props.putAll(KafkaConfigHelper.commonProducer());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
    putAcknowledgedProducerTimeouts(props);
    return props;
  }

  private void putAcknowledgedProducerTimeouts(Map<String, Object> props) {
    long timeoutMs = consumersProperties.getProducerAckTimeout().toMillis();
    int timeoutMsInt = Math.toIntExact(timeoutMs);
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, timeoutMs);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMsInt);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMsInt);
    props.put(ProducerConfig.LINGER_MS_CONFIG, 0L);
  }

  @Bean("acknowledgedByteArrayKafkaTemplate")
  KafkaTemplate<String, byte[]> acknowledgedByteArrayKafkaTemplate(
      @Qualifier("acknowledgedByteArrayProducerFactory")
          DefaultKafkaProducerFactory<String, byte[]> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean("acknowledgedByteArrayProducerFactoryResetter")
  ProducerFactoryResetter acknowledgedByteArrayProducerFactoryResetter(
      @Qualifier("acknowledgedByteArrayProducerFactory")
          DefaultKafkaProducerFactory<String, byte[]> producerFactory) {
    return new CoordinatedProducerFactoryResetter(producerFactory::reset);
  }

  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.putAll(baseKafkaConfig.props());

    var cf =
        new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new ErrorHandlingDeserializer<>(new ByteArrayDeserializer()));

    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(cf);
    return factory;
  }

  @Override
  public void setBeanFactory(@NonNull BeanFactory beanFactory) {
    this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
  }

  @Override
  public void afterPropertiesSet() {
    try {
      consumersProperties
          .getListeners()
          .forEach(
              (name, listenerProps) -> {
                String valueType = listenerProps.getValueType();
                String beanName = name + "Factory";

                if (beanFactory.containsSingleton(beanName)) {
                  throw new IllegalStateException("Duplicate listener: " + beanName);
                }

                if (valueType.equals("feature")) {
                  initializeAndRegisterSingleton(beanName, featureListenerFactory(listenerProps));
                } else {
                  initializeAndRegisterSingleton(
                      beanName, byteArrayListenerFactory(name, listenerProps));
                }
              });
      registerDltIndexContainers();
    } catch (Exception e) {
      throw new IllegalStateException("Failed creating Kafka listener factories", e);
    }
  }

  private void registerDltIndexContainers() {
    var dltProps = consumersProperties.getDltIndexing();
    if (!Boolean.TRUE.equals(dltProps.getEnabled())) {
      return;
    }
    FixedDeadLetterConsumer dltConsumer = fixedDeadLetterConsumer.getObject();
    var dltTopics = new LinkedHashSet<String>();
    consumersProperties.getListeners().entrySet().stream()
        .filter(entry -> dltProps.getEnabledListeners().contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .forEach(listenerProps -> dltTopics.add(listenerProps.getTopic() + DLT_SUFFIX));
    dltTopics.forEach(
        dltTopic -> {
          String beanName = dltIndexContainerBeanName(dltTopic);
          if (beanFactory.containsSingleton(beanName)) {
            throw new IllegalStateException("Duplicate listener: " + beanName);
          }
          initializeAndRegisterSingleton(
              beanName, dltIndexContainer(dltTopic, dltProps, dltConsumer));
        });
  }

  /**
   * Runs the full Spring initialization lifecycle on a programmatically created bean, then
   * registers the result as a singleton.
   *
   * <p>{@link ConfigurableListableBeanFactory#registerSingleton} on its own bypasses every {@code
   * BeanPostProcessor}, including {@code ApplicationContextAwareProcessor}. A listener factory
   * registered that way never receives an {@code ApplicationEventPublisher}, and {@code
   * AbstractKafkaListenerContainerFactory} copies its publisher onto the containers it creates only
   * when that publisher is non-null. The omission is therefore silent: containers start, join their
   * group, and consume normally, but never emit container lifecycle events - including the {@code
   * ListenerContainerIdleEvent}s the watchbox detection heartbeat relies on for its no-traffic
   * liveness evidence. The containers also lose {@code ApplicationContext}, without which {@code
   * MicrometerHolder} cannot register listener timers.
   *
   * <p>The instance returned by {@code initializeBean} is the one registered, never the argument: a
   * post-processor may return a wrapper, and registering the unwrapped original would reintroduce
   * exactly this class of silent divergence between the bean that was configured and the bean that
   * is used.
   */
  private void initializeAndRegisterSingleton(String beanName, Object bean) {
    Object initialized = beanFactory.initializeBean(bean, beanName);
    beanFactory.registerSingleton(beanName, initialized);
  }

  private MessageListenerContainer dltIndexContainer(
      String dltTopic,
      KafkaConsumersProperties.DeadLetterIndexingProps dltProps,
      FixedDeadLetterConsumer dltConsumer) {
    var cfg = new HashMap<String, Object>();
    var groupId = fixedDltGroupId(dltTopic, dltProps);
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, dltProps.getAutoOffsetReset());
    cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, dltProps.getMaxPollRecords());
    cfg.putAll(baseKafkaConfig.props());

    var consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            cfg,
            new StringDeserializer(),
            new ErrorHandlingDeserializer<>(new ByteArrayDeserializer()));
    var containerProps = new ContainerProperties(dltTopic);
    containerProps.setGroupId(groupId);
    containerProps.setAckMode(ContainerProperties.AckMode.MANUAL);
    containerProps.setMessageListener(
        (BatchAcknowledgingMessageListener<String, byte[]>) dltConsumer::index);
    var container = new ConcurrentMessageListenerContainer<>(consumerFactory, containerProps);
    container.setConcurrency(dltProps.getConcurrency());
    container.setCommonErrorHandler(dltIndexingErrorHandlerFactory.create(dltTopic, dltProps));
    return container;
  }

  private static String dltIndexContainerBeanName(String dltTopic) {
    return dltTopic.replaceAll("[^A-Za-z0-9]", "_") + DLT_CONTAINER_SUFFIX;
  }

  private static String fixedDltGroupId(
      String dltTopic, KafkaConsumersProperties.DeadLetterIndexingProps dltProps) {
    return dltTopic.replaceAll("[^A-Za-z0-9._-]", "_") + dltProps.getGroupIdSuffix();
  }

  private ConcurrentKafkaListenerContainerFactory<String, Feature> featureListenerFactory(
      KafkaConsumersProperties.ListenerProps listenerProps) {
    var factory =
        kafkaListenerFactoryBuilder.featureFactory(
            baseKafkaConfig.props(), listenerProps, 1000L, geoJsonDltTemplate());
    if (Boolean.TRUE.equals(listenerProps.getRetryInPlaceFailures())) {
      factory.setBatchInterceptor(watchboxRetainedRetryTracker);
      factory.getContainerProperties().setConsumerRebalanceListener(watchboxRetainedRetryTracker);
    }
    return factory;
  }

  private ConcurrentKafkaListenerContainerFactory<String, byte[]> byteArrayListenerFactory(
      String listenerName, KafkaConsumersProperties.ListenerProps listenerProps) {
    if ("jobs".equals(listenerName) || "history".equals(listenerName)) {
      return kafkaListenerFactoryBuilder.byteArrayFactory(
          baseKafkaConfig.props(),
          listenerProps,
          1000L,
          cacheHydrationRecoverer(newByteArrayDltTemplate()));
    }
    return kafkaListenerFactoryBuilder.byteArrayFactory(
        baseKafkaConfig.props(), listenerProps, 1000L, newByteArrayDltTemplate());
  }

  /** DLT template injected into MapRawToGeoHandler for manual dead-letter publishing. */
  @Bean("rawDltTemplate")
  KafkaTemplate<String, byte[]> rawDltTemplate(
      @Qualifier("acknowledgedByteArrayKafkaTemplate")
          KafkaTemplate<String, byte[]> kafkaTemplate) {
    return kafkaTemplate;
  }

  /** DLT template injected into feature consumers that manually dead-letter bad records. */
  @Bean("featureDltTemplate")
  KafkaTemplate<String, Feature> featureDltTemplate() {
    return geoJsonDltTemplate();
  }

  // Extracted to a plain method so afterPropertiesSet() can create DLT templates without
  // calling through the @Bean proxy, which would create a circular dependency on this class.
  private KafkaTemplate<String, byte[]> newByteArrayDltTemplate() {
    var cfg = new HashMap<String, Object>();
    cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    cfg.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
  }

  private KafkaTemplate<String, Feature> geoJsonDltTemplate() {
    var cfg = new HashMap<String, Object>();
    cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaGeoJsonSerializer.class);
    cfg.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
  }

  private ConsumerRecordRecoverer cacheHydrationRecoverer(
      KafkaTemplate<String, byte[]> dltTemplate) {
    var recoverer = kafkaListenerFactoryBuilder.recovererFor(dltTemplate);

    return (sourceMessage, exception) -> {
      recoverer.accept(sourceMessage, null, exception);
      try {
        if (!(sourceMessage.value() instanceof byte[] payload)) {
          return;
        }
        JobSubmission job = objectMapper.readValue(payload, JobSubmission.class);
        JobStatusNotificationPublisher notifications =
            jobStatusNotificationPublisher.getIfAvailable();
        if (notifications != null) {
          notifications.notifyFailure(job, exception);
        }
      } catch (Exception notificationError) {
        throw new IllegalStateException("Failed to notify terminal job failure", notificationError);
      }
    };
  }
}
