// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.kafka;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.policy.kafka.KafkaProducerByteRatePolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.KafkaConnectionExtractorCapability;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Native Kafka extractor for a producer the host provisions on the broker. The request supplies
 * nothing but an optional throttle: the topic and the producer's credentials are the host's to
 * create.
 *
 * <p>The throttle is not yet applied on every broker: Azure Event Hubs has no per-producer quota,
 * so its provisioner ignores the rate. A rate is still validated, so it is ready to apply once a
 * broker supports it.
 */
public final class NativeKafkaProducerExtractor implements KafkaConnectionExtractorCapability {

  public static final UUID EXTRACTOR_ID = UUID.fromString("c51e8f3b-2a74-4d09-b6e2-8f3a1d7c5e90");

  private final NativeConnectionSchema schema;

  public NativeKafkaProducerExtractor(NativeConnectionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  @Override
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  @Override
  public UUID id() {
    return EXTRACTOR_ID;
  }

  /**
   * Builds the declaration on call rather than holding it in a constant, so a malformed field
   * reaches the registry and is rejected with the integration and extractor named.
   */
  @Override
  public ExtractorDescriptor descriptor() {
    return new ExtractorDescriptor(
        EXTRACTOR_ID,
        "Kafka producer",
        List.of(
            new TextField(
                KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD,
                "Producer byte rate (bytes/s)",
                false)));
  }

  @Override
  public void validate(ConnectionInput input) throws ConnectionInputException {
    producerByteRate(input);
  }

  @Override
  public KafkaConnectionMaterial extract(ConnectionInput input) throws ConnectionInputException {
    return new KafkaConnectionMaterial(new ScramSecurityMaterial(producerByteRate(input)));
  }

  private static Long producerByteRate(ConnectionInput input) throws ConnectionInputException {
    return KafkaProducerByteRatePolicy.producerByteRate(
        input.value(KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD).orElse(null));
  }
}
