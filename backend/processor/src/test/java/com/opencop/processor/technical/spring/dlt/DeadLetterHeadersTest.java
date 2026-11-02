// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.InvalidMappingDiagnosticException;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnostic;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingInputException;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.RawDataParsingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.Test;

class DeadLetterHeadersTest {

  @Test
  void manualRawHeadersPreserveOnlyAllowListedInvalidMappingMetadata() {
    var failure =
        new RuntimeException(
            "wrapped",
            new InvalidMappingDiagnosticException(
                new MappingDiagnostic(
                    "INVALID_FIXTURE_MAPPING",
                    MappingDiagnosticSeverity.ERROR,
                    "Invalid mapping",
                    Map.of("field", "detail.shape", "type", "u-d-c-c", "uid", "secret"))));

    var headers = DeadLetterHeaders.forManualRaw(record("opencop.event.external.raw"), failure);

    assertThat(headerValue(headers, DeadLetterHeaders.FAILURE_KIND)).isEqualTo("MAPPING_ERROR");
    assertThat(headerValue(headers, DeadLetterHeaders.DIAGNOSTIC_CATEGORY))
        .isEqualTo("INVALID_MAPPING");
    assertThat(headerValue(headers, DeadLetterHeaders.DIAGNOSTIC_FIELD)).isEqualTo("detail.shape");
    assertThat(headerValue(headers, DLT_EXCEPTION_MESSAGE))
        .contains("Decoder rejected mapping", "field=detail.shape")
        .doesNotContain("INVALID_FIXTURE_MAPPING", "u-d-c-c", "secret");
  }

  @Test
  void byodRecoveryHeadersClassifyUnspecifiedMapperInputFailuresAsProcessing() {
    var failure = new RuntimeException("wrapped", new MappingInputException());

    var headers = DeadLetterHeaders.forByodRecoverer(record("opencop.byod.source.events"), failure);

    assertThat(headerValue(headers, DeadLetterHeaders.STAGE)).isEqualTo("BYOD");
    assertThat(headerValue(headers, DeadLetterHeaders.FAILURE_KIND)).isEqualTo("PROCESSING");
  }

  @Test
  void preservesCodecFailureReasonsThroughRealTimeWrappersForClassification() {
    var malformed = realTimeFailure(MappingInputException.Reason.MALFORMED_PAYLOAD);
    var parserConfiguration = realTimeFailure(MappingInputException.Reason.MAPPER_CONFIGURATION);

    var malformedHeaders =
        DeadLetterHeaders.forByodRecoverer(record("opencop.byod.source.events"), malformed);
    var configurationHeaders =
        DeadLetterHeaders.forByodRecoverer(
            record("opencop.byod.source.events"), parserConfiguration);

    assertThat(headerValue(malformedHeaders, DeadLetterHeaders.FAILURE_KIND))
        .isEqualTo("MALFORMED");
    assertThat(headerValue(configurationHeaders, DeadLetterHeaders.FAILURE_KIND))
        .isEqualTo("INFRA");
    assertThat(headerValue(malformedHeaders, DLT_EXCEPTION_MESSAGE)).isEqualTo("MALFORMED_PAYLOAD");
    assertThat(headerValue(configurationHeaders, DLT_EXCEPTION_MESSAGE))
        .isEqualTo("MAPPER_CONFIGURATION");
  }

  private static RawDataParsingException realTimeFailure(MappingInputException.Reason reason) {
    return new RawDataParsingException("event-id", new MappingInputException(reason));
  }

  private static ConsumerRecord<String, byte[]> record(String topic) {
    return new ConsumerRecord<>(topic, 0, 42L, "key", new byte[] {1});
  }

  private static String headerValue(Headers headers, String name) {
    return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
  }
}
