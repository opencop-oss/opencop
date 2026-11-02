// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.MapperIngressRule;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.ProtocolSetupResponse;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.TcpIngressRule;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Annotations compile whatever they claim, so these pin the published examples to the contract they
 * document: the response records and the rejection reasons.
 */
class IntegrationConnectionSetupOpenApiExamplesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void everyPublishedExampleIsValidJson() throws Exception {
    for (var field : IntegrationConnectionSetupOpenApiExamples.class.getDeclaredFields()) {
      if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
        assertThat(objectMapper.readValue((String) field.get(null), Object.class))
            .as(field.getName())
            .isNotNull();
      }
    }
  }

  @Test
  void theSuccessExampleDeserializesIntoTheResponseItDocuments() {
    var response = successExample();

    assertThat(response.schemaVersion()).isEqualTo(2);
    assertThat(response.protocols())
        .<Class<?>>extracting(Object::getClass)
        .containsExactly(
            ProtocolSetupResponse.Tcp.class,
            ProtocolSetupResponse.Udp.class,
            ProtocolSetupResponse.Rtsp.class);
    var tcp = (ProtocolSetupResponse.Tcp) response.protocols().getFirst();
    assertThat(tcp.ingress())
        .hasExactlyElementsOfTypes(
            TcpIngressRule.ExactPair.class,
            TcpIngressRule.AnyMapperWith.class,
            TcpIngressRule.AnyMapperCustomFraming.class);
    assertThat(tcp.egress()).isNotNull();
    assertThat(tcp.egress().allowedMappers()).hasSize(1);
    var udp = (ProtocolSetupResponse.Udp) response.protocols().get(1);
    assertThat(udp.ingress()).containsExactly(new MapperIngressRule.AnyMapper());
  }

  /** A protocol that writes shows every mode; one that cannot shows read-only access alone. */
  @Test
  void theSuccessExampleShowsWhichAccessModesEachProtocolAllows() {
    assertThat(successExample().protocols())
        .extracting(ProtocolSetupResponse::accessModes)
        .containsExactly(
            List.of(AccessMode.READ, AccessMode.WRITE, AccessMode.READ_WRITE),
            List.of(AccessMode.READ),
            List.of(AccessMode.READ));
  }

  /** Only TCP frames and writes, so no other kind may carry framings or an egress rule. */
  @Test
  void theSuccessExampleCarriesFramingsAndEgressOnlyOnTcp() {
    var protocols =
        objectMapper
            .readTree(IntegrationConnectionSetupOpenApiExamples.CONNECTION_SETUP_RESPONSE)
            .path("protocols");

    for (var protocol : protocols) {
      var isTcp = "TCP".equals(protocol.path("kind").asString());
      assertThat(protocol.has("framings")).as(protocol.path("kind").asString()).isEqualTo(isTcp);
      assertThat(protocol.has("egress")).as(protocol.path("kind").asString()).isEqualTo(isTcp);
    }
  }

  @Test
  void theSuccessExampleResolvesEveryFramingItsRulesReference() {
    var tcp = (ProtocolSetupResponse.Tcp) successExample().protocols().getFirst();

    // Nothing else can resolve a framing identifier, so every one referenced must be listed.
    assertThat(tcp.framings())
        .extracting(IntegrationConnectionSetupResponse.FramingResponse::id)
        .containsExactlyInAnyOrderElementsOf(
            tcp.ingress().stream()
                .flatMap(
                    rule ->
                        switch (rule) {
                          case TcpIngressRule.ExactPair pair -> Stream.of(pair.framingId());
                          case TcpIngressRule.AnyMapperWith any -> Stream.of(any.framingId());
                          case TcpIngressRule.AnyMapperCustomFraming ignored ->
                              Stream.<UUID>empty();
                        })
                .distinct()
                .toList());
  }

  @Test
  void theSuccessExampleShowsEveryIngressRuleTypeAClientMustHandle() {
    var documented =
        successExample().protocols().stream()
            .flatMap(protocol -> ingressOf(protocol).stream())
            .<Class<?>>map(Object::getClass)
            .distinct()
            .toList();

    assertThat(documented)
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(
                    Stream.of(TcpIngressRule.class.getPermittedSubclasses()),
                    Stream.of(MapperIngressRule.class.getPermittedSubclasses()))
                .toList());
  }

  // Checks the example against the key builder; the literal scheme is pinned in the response test.
  @Test
  void theSuccessExampleKeysEveryLabelTheWayTheResponseDerivesIt() {
    var response = successExample();
    assertThat(response.protocols()).isNotEmpty();

    for (var protocol : response.protocols()) {
      for (var extractor : protocol.extractors()) {
        assertThat(extractor.labelKey())
            .isEqualTo(
                IntegrationTextKeys.extractorLabel(response.integrationId(), extractor.id()));
        for (var field : extractor.fields()) {
          assertThat(field.labelKey())
              .isEqualTo(
                  IntegrationTextKeys.fieldLabel(
                      response.integrationId(), extractor.id(), field.name()));
        }
      }
    }
  }

  @Test
  void theSuccessExampleShowsEveryFieldKindAClientMustRenderAComponentFor() {
    var documentedKinds =
        fields().stream()
            .map(IntegrationConnectionSetupResponse.ExtractorFieldResponse::kind)
            .distinct()
            .toList();

    // The examples are the client's reference for picking a form component per kind, so every
    // kind must actually appear in one.
    assertThat(documentedKinds).containsExactlyInAnyOrder(ExtractorFieldKind.values());
  }

  @Test
  void theSuccessExampleCarriesFormatsOnlyForTheFileBearingKinds() {
    var fields = fields();

    assertThat(fields)
        .filteredOn(field -> field.kind() == ExtractorFieldKind.FILE)
        .allSatisfy(
            field -> {
              assertThat(field.extensions()).isNotEmpty();
              assertThat(field.mediaTypes()).isNotEmpty();
            });
    assertThat(fields)
        .filteredOn(field -> field.kind() == ExtractorFieldKind.FILE_LIST)
        .allSatisfy(field -> assertThat(field.extensions()).isNotEmpty());
    assertThat(fields)
        .filteredOn(
            field ->
                field.kind() == ExtractorFieldKind.TEXT
                    || field.kind() == ExtractorFieldKind.SECRET)
        .allSatisfy(
            field -> {
              assertThat(field.extensions()).isNull();
              assertThat(field.mediaTypes()).isNull();
            });
  }

  @Test
  void theSuccessExampleNamesNoFieldTheContractDoesNotDefine() {
    var names = propertyNames(IntegrationConnectionSetupOpenApiExamples.CONNECTION_SETUP_RESPONSE);

    assertThat(names)
        .containsExactlyInAnyOrder(
            "integrationId",
            "schemaVersion",
            "protocols",
            "accessModes",
            "extractors",
            "ingress",
            "type",
            "egress",
            "anyMapper",
            "allowedMappers",
            "framings",
            "mode",
            "id",
            "label",
            "labelKey",
            "fields",
            "name",
            "required",
            "kind",
            "extensions",
            "mediaTypes",
            "standardKey",
            "mapperId",
            "framingId");
  }

  @Test
  void everyRejectionReasonIsDocumentedByExactlyOneExample() {
    var documented =
        diagnosticExamples()
            .map(example -> objectMapper.readValue(example, GlobalErrorResponse.class))
            .toList();

    assertThat(documented).hasSize(IntegrationConnectionSetupRejection.values().length);
    for (var reason : IntegrationConnectionSetupRejection.values()) {
      assertThat(documented)
          .as(reason.name())
          .containsOnlyOnce(new GlobalErrorResponse(reason.code(), reason.message()));
    }
  }

  @Test
  void theSuccessExampleKeysEveryStandardFieldByItsStandardName() {
    assertThat(fields())
        .filteredOn(field -> field.standardKey() != null)
        .isNotEmpty()
        .allSatisfy(field -> assertThat(field.name()).isEqualTo(field.standardKey().fieldName()));
  }

  private IntegrationConnectionSetupResponse successExample() {
    return objectMapper.readValue(
        IntegrationConnectionSetupOpenApiExamples.CONNECTION_SETUP_RESPONSE,
        IntegrationConnectionSetupResponse.class);
  }

  private static List<?> ingressOf(ProtocolSetupResponse protocol) {
    return switch (protocol) {
      case ProtocolSetupResponse.Tcp tcp -> tcp.ingress();
      case ProtocolSetupResponse.Udp udp -> udp.ingress();
      case ProtocolSetupResponse.Rtsp rtsp -> rtsp.ingress();
      case ProtocolSetupResponse.Kafka kafka -> kafka.ingress();
    };
  }

  private List<IntegrationConnectionSetupResponse.ExtractorFieldResponse> fields() {
    return successExample().protocols().stream()
        .flatMap(protocol -> protocol.extractors().stream())
        .flatMap(extractor -> extractor.fields().stream())
        .toList();
  }

  private static Stream<String> diagnosticExamples() {
    return Stream.of(
        IntegrationConnectionSetupOpenApiExamples.NOT_FOUND_RESPONSE,
        IntegrationConnectionSetupOpenApiExamples.INACTIVE_RESPONSE,
        IntegrationConnectionSetupOpenApiExamples.UNAVAILABLE_RESPONSE,
        IntegrationConnectionSetupOpenApiExamples.PROTOCOL_UNSUPPORTED_RESPONSE);
  }

  private List<String> propertyNames(String json) {
    var names = new ArrayList<String>();
    collectPropertyNames(objectMapper.readValue(json, Object.class), names);
    return names.stream().distinct().toList();
  }

  private static void collectPropertyNames(Object node, List<String> names) {
    switch (node) {
      case Map<?, ?> map ->
          map.forEach(
              (key, value) -> {
                names.add(String.valueOf(key));
                collectPropertyNames(value, names);
              });
      case List<?> list -> list.forEach(element -> collectPropertyNames(element, names));
      case null, default -> {
        // Scalars and nulls carry no property names.
      }
    }
  }
}
