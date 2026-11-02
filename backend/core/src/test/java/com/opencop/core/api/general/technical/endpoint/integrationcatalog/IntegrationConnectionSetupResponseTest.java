// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.EgressRuleResponse;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.ExtractorFieldResponse;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.MapperIngressRule;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.ProtocolSetupResponse;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupResponse.TcpIngressRule;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileListField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.SecretField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins how a described setup reaches the wire.
 *
 * <p>Every identifier here is chosen so that canonical-text order and {@link UUID#compareTo} order
 * disagree: {@code HIGH} has its high bit set, so natural order places it first while text order
 * places it last. Identifiers that all begin with the same zero block cannot tell the two apart.
 */
class IntegrationConnectionSetupResponseTest {

  private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID MIDDLE = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
  private static final UUID HIGH = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

  private static final IntegrationConnectionSchemaKey SCHEMA =
      new IntegrationConnectionSchemaKey(
          UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31"), 2);
  private static final ExtractorDescriptor EXTRACTOR =
      new ExtractorDescriptor(
          LOW,
          "Only",
          List.of(
              StandardExtractorField.HOST.declare(true),
              new SecretField("passphrase", "Passphrase", true)));

  /** Literal strings on purpose: clients put these keys in locale files. */
  private static final String KEY_PREFIX =
      "integrations.9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31"
          + ".extractors.00000000-0000-4000-8000-000000000001";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void ordersTcpRulesByTypeThenByMapperThenFramingInCanonicalTextOrder() {
    var declared = new LinkedHashSet<FramingRule>();
    declared.add(new FramingRule.AnyMapperCustomFraming());
    declared.add(new FramingRule.AnyMapperWith(HIGH));
    declared.add(new MapperFramingPair(MIDDLE, HIGH));
    declared.add(new FramingRule.AnyMapperWith(LOW));
    declared.add(new MapperFramingPair(HIGH, LOW));
    declared.add(new MapperFramingPair(MIDDLE, LOW));

    var ingress = tcp(new MapperFramingDeclaration(declared)).ingress();

    assertThat(ingress)
        .containsExactly(
            new TcpIngressRule.ExactPair(MIDDLE, LOW),
            new TcpIngressRule.ExactPair(MIDDLE, HIGH),
            new TcpIngressRule.ExactPair(HIGH, LOW),
            new TcpIngressRule.AnyMapperWith(LOW),
            new TcpIngressRule.AnyMapperWith(HIGH),
            new TcpIngressRule.AnyMapperCustomFraming());
  }

  @Test
  void answersEachMapperOnlyTransportWithItsOwnKind() {
    var rule = MapperDeclaration.openMapper();

    assertThat(soleProtocol(TransportProtocol.UDP, rule))
        .isInstanceOf(ProtocolSetupResponse.Udp.class);
    assertThat(soleProtocol(TransportProtocol.RTSP, rule))
        .isInstanceOf(ProtocolSetupResponse.Rtsp.class);
    assertThat(soleProtocol(TransportProtocol.KAFKA, rule))
        .isInstanceOf(ProtocolSetupResponse.Kafka.class);
  }

  @Test
  void listsTheMappersOfAClosedMapperOnlyRuleInCanonicalTextOrder() {
    var udp =
        (ProtocolSetupResponse.Udp)
            soleProtocol(
                TransportProtocol.UDP, MapperDeclaration.closedTo(Set.of(HIGH, LOW, MIDDLE)));

    assertThat(udp.ingress())
        .containsExactly(
            new MapperIngressRule.Mapper(LOW),
            new MapperIngressRule.Mapper(MIDDLE),
            new MapperIngressRule.Mapper(HIGH));
  }

  /** An open rule already permits every mapper, so listing some as well would read as a limit. */
  @Test
  void answersAnOpenMapperOnlyRuleWithAnyMapperAlone() {
    var kafka =
        (ProtocolSetupResponse.Kafka)
            soleProtocol(TransportProtocol.KAFKA, new MapperDeclaration(Set.of(LOW), true));

    assertThat(kafka.ingress()).containsExactly(new MapperIngressRule.AnyMapper());
  }

  @Test
  void givesATcpIntegrationThatCannotWriteNoEgressRule() {
    assertThat(tcp(MapperFramingDeclaration.of(new FramingRule.AnyMapperWith(LOW))).egress())
        .isNull();
  }

  @Test
  void publishesAClosedEgressRuleWithItsMappersInCanonicalTextOrder() {
    var egress =
        tcp(MapperFramingDeclaration.of(new FramingRule.AnyMapperWith(LOW))
                .withEgress(EgressMapperRule.closedTo(Set.of(HIGH, LOW))))
            .egress();

    assertThat(egress).isEqualTo(new EgressRuleResponse(false, List.of(LOW, HIGH)));
  }

  @Test
  void publishesAnOpenEgressRuleWithoutListingMappers() {
    var egress =
        tcp(MapperFramingDeclaration.of(new FramingRule.AnyMapperWith(LOW))
                .withEgress(EgressMapperRule.openMapper()))
            .egress();

    assertThat(egress).isEqualTo(new EgressRuleResponse(true, List.of()));
  }

  @Test
  void serializesTheAccessModesATcpIntegrationThatWritesAllowsAsTheirWireValues() {
    var tcp =
        json(setup(
                tcpSetup(
                    MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming())
                        .withEgress(EgressMapperRule.openMapper()))))
            .path("protocols")
            .path(0);

    assertThat(tcp.path("accessModes").toString()).isEqualTo("[\"R\",\"W\",\"RW\"]");
  }

  @Test
  void offersOnlyReadAccessOverATransportThatCannotWrite() {
    assertThat(tcp(MapperFramingDeclaration.of(new FramingRule.AnyMapperWith(LOW))).accessModes())
        .containsExactly(AccessMode.READ);
    assertThat(soleProtocol(TransportProtocol.UDP, MapperDeclaration.openMapper()).accessModes())
        .containsExactly(AccessMode.READ);
  }

  @Test
  void keysOnlyAStandardFieldByItsStandardName() {
    var fields =
        soleProtocol(TransportProtocol.UDP, MapperDeclaration.openMapper())
            .extractors()
            .getFirst()
            .fields();

    assertThat(fields.get(0).standardKey()).isEqualTo(StandardExtractorField.HOST);
    assertThat(fields.get(1).standardKey()).isNull();
  }

  @ParameterizedTest
  @EnumSource(TransportProtocol.class)
  void keysTheExtractorLabelOverEveryTransport(TransportProtocol transport) {
    var protocol =
        transport == TransportProtocol.TCP
            ? tcpSetup(MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming()))
            : mapperOnly(transport, MapperDeclaration.openMapper());

    var extractor = json(setup(protocol)).path("protocols").path(0).path("extractors").path(0);

    assertThat(extractor.path("labelKey").asString()).isEqualTo(KEY_PREFIX + ".label");
  }

  @Test
  void keysEveryKindOfFieldByItsName() {
    var extractor =
        new ExtractorDescriptor(
            LOW,
            "Every kind",
            List.of(
                StandardExtractorField.HOST.declare(true),
                new SecretField("passphrase", "Passphrase", true),
                new FileField("bundle", "Bundle", true, Set.of(".p12"), Set.of()),
                new FileListField("anchors", "Anchors", false, Set.of(".pem"), Set.of())));

    var fields =
        IntegrationConnectionSetupResponse.from(
                setup(
                    new ProtocolConnectionSetup.Udp(
                        List.of(extractor),
                        MapperDeclaration.openMapper(),
                        List.of(AccessMode.READ))))
            .protocols()
            .getFirst()
            .extractors()
            .getFirst()
            .fields();

    assertThat(fields)
        .extracting(ExtractorFieldResponse::label, ExtractorFieldResponse::labelKey)
        .containsExactly(
            tuple("Host", KEY_PREFIX + ".fields.host"),
            tuple("Passphrase", KEY_PREFIX + ".fields.passphrase"),
            tuple("Bundle", KEY_PREFIX + ".fields.bundle"),
            tuple("Anchors", KEY_PREFIX + ".fields.anchors"));
  }

  /** A mapper-only kind has no framing or egress to carry, so neither key reaches the wire. */
  @Test
  void serializesAMapperOnlyKindWithoutFramingsOrEgress() {
    var udp =
        json(setup(
                new ProtocolConnectionSetup.Udp(
                    List.of(EXTRACTOR),
                    MapperDeclaration.closedTo(Set.of(LOW)),
                    List.of(AccessMode.READ))))
            .path("protocols")
            .path(0);

    assertThat(udp.path("kind").asString()).isEqualTo("UDP");
    assertThat(udp.path("ingress").path(0).path("type").asString()).isEqualTo("MAPPER");
    assertThat(udp.path("ingress").path(0).path("mapperId").asString()).isEqualTo(LOW.toString());
    assertThat(udp.has("framings")).isFalse();
    assertThat(udp.has("egress")).isFalse();
    assertThat(udp.path("extractors").path(0).path("fields").path(1).has("standardKey")).isFalse();
  }

  @Test
  void serializesATcpIntegrationThatCannotWriteWithAnExplicitNullEgress() {
    var tcp =
        json(setup(tcpSetup(MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming()))))
            .path("protocols")
            .path(0);

    assertThat(tcp.path("kind").asString()).isEqualTo("TCP");
    assertThat(tcp.path("ingress").path(0).path("type").asString())
        .isEqualTo("ANY_MAPPER_CUSTOM_FRAMING");
    assertThat(tcp.has("egress")).isTrue();
    assertThat(tcp.path("egress").isNull()).isTrue();
  }

  @Test
  void readsBackEveryKindAndRuleTypeItWrites() {
    var response =
        IntegrationConnectionSetupResponse.from(
            new IntegrationConnectionSetup(
                SCHEMA,
                List.of(
                    tcpSetup(
                        MapperFramingDeclaration.of(
                                new MapperFramingPair(LOW, HIGH),
                                new FramingRule.AnyMapperWith(HIGH),
                                new FramingRule.AnyMapperCustomFraming())
                            .withEgress(EgressMapperRule.openMapper())),
                    mapperOnly(TransportProtocol.UDP, MapperDeclaration.openMapper()),
                    mapperOnly(TransportProtocol.RTSP, MapperDeclaration.closedTo(Set.of(LOW))),
                    mapperOnly(TransportProtocol.KAFKA, MapperDeclaration.openMapper()))));

    var roundTripped =
        objectMapper.readValue(
            objectMapper.writeValueAsString(response), IntegrationConnectionSetupResponse.class);

    assertThat(roundTripped).isEqualTo(response);
  }

  private JsonNode json(IntegrationConnectionSetup setup) {
    return objectMapper.readTree(
        objectMapper.writeValueAsString(IntegrationConnectionSetupResponse.from(setup)));
  }

  private static ProtocolSetupResponse.Tcp tcp(MapperFramingDeclaration rule) {
    var response = IntegrationConnectionSetupResponse.from(setup(tcpSetup(rule)));
    return (ProtocolSetupResponse.Tcp) response.protocols().getFirst();
  }

  private static ProtocolSetupResponse soleProtocol(
      TransportProtocol protocol, MapperDeclaration rule) {
    var response = IntegrationConnectionSetupResponse.from(setup(mapperOnly(protocol, rule)));
    assertThat(response.protocols()).hasSize(1);
    return response.protocols().getFirst();
  }

  private static IntegrationConnectionSetup setup(ProtocolConnectionSetup protocol) {
    return new IntegrationConnectionSetup(SCHEMA, List.of(protocol));
  }

  /** The rule's framings are not resolved here; the policy is what requires them. */
  private static ProtocolConnectionSetup tcpSetup(MapperFramingDeclaration rule) {
    return new ProtocolConnectionSetup.Tcp(
        List.of(EXTRACTOR),
        rule,
        List.of(),
        AllowedAccessModesPolicy.allowedModes(TransportProtocol.TCP, rule));
  }

  private static ProtocolConnectionSetup mapperOnly(
      TransportProtocol protocol, MapperDeclaration rule) {
    return switch (protocol) {
      case UDP ->
          new ProtocolConnectionSetup.Udp(
              List.of(EXTRACTOR), rule, AllowedAccessModesPolicy.allowedModes(protocol, rule));
      case RTSP ->
          new ProtocolConnectionSetup.Rtsp(
              List.of(EXTRACTOR), rule, AllowedAccessModesPolicy.allowedModes(protocol, rule));
      case KAFKA ->
          new ProtocolConnectionSetup.Kafka(
              List.of(EXTRACTOR), rule, AllowedAccessModesPolicy.allowedModes(protocol, rule));
      case TCP -> throw new IllegalArgumentException("TCP is framed");
    };
  }
}
