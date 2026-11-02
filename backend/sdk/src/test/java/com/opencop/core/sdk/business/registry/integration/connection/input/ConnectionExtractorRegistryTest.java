// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpConnectionMaterial;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import com.opencop.core.sdk.ports.integration.connection.input.KafkaConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.RtspConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.UdpConnectionExtractorCapability;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConnectionExtractorRegistryTest {

  private static final String FIRST_SCHEMA_ID = "11111111-1111-1111-1111-111111111111";
  private static final String SECOND_SCHEMA_ID = "22222222-2222-2222-2222-222222222222";

  @Test
  void returnsTheExtractorsOfOneProtocolInRegistrationOrder() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP, TransportProtocol.UDP));
    var firstTcp = tcpExtractor(schema, uuid("first-tcp"));
    var udp = udpExtractor(schema, uuid("udp"));
    var secondTcp = tcpExtractor(schema, uuid("second-tcp"));
    var registry =
        new ConnectionExtractorRegistry(List.of(schema), List.of(firstTcp, udp, secondTcp));

    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.TCP))
        .containsExactly(firstTcp, secondTcp);
    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.UDP))
        .containsExactly(udp);
  }

  @Test
  void returnsNoExtractorsForAProtocolTheSchemaDoesNotRegister() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));
    var registry =
        new ConnectionExtractorRegistry(
            List.of(schema), List.of(extractor(schema, TransportProtocol.UDP, uuid("udp"))));

    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.KAFKA)).isEmpty();
  }

  /** The key fixes the material type, so a creator receives an extractor it can call as is. */
  @Test
  void resolvesAnExtractorTypedByItsProtocol() throws Exception {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));
    var udp = udpExtractor(schema, uuid("udp"));
    var registry = new ConnectionExtractorRegistry(List.of(schema), List.of(udp));

    Optional<ConnectionExtractorCapability<ConnectionInput, UdpConnectionMaterial>> resolved =
        registry.findById(schema.reference(), ExtractorProtocol.UDP, uuid("udp"));

    assertThat(resolved).contains(udp);
    assertThat(resolved.orElseThrow().extract(new TcpConnectionInput(List.of(), List.of())))
        .isNull();
  }

  @Test
  void resolvesEveryNonTcpExtractorTypedByItsProtocol() {
    var schema =
        schema(
            FIRST_SCHEMA_ID,
            Set.of(TransportProtocol.UDP, TransportProtocol.RTSP, TransportProtocol.KAFKA));
    var udp = udpExtractor(schema, uuid("udp"));
    var rtsp = rtspExtractor(schema, uuid("rtsp"));
    var kafka = kafkaExtractor(schema, uuid("kafka"));
    var registry = new ConnectionExtractorRegistry(List.of(schema), List.of(udp, rtsp, kafka));

    Optional<ConnectionExtractorCapability<ConnectionInput, UdpConnectionMaterial>> resolvedUdp =
        registry.findById(schema.reference(), ExtractorProtocol.UDP, uuid("udp"));
    Optional<ConnectionExtractorCapability<ConnectionInput, RtspConnectionMaterial>> resolvedRtsp =
        registry.findById(schema.reference(), ExtractorProtocol.RTSP, uuid("rtsp"));
    Optional<ConnectionExtractorCapability<ConnectionInput, KafkaConnectionMaterial>>
        resolvedKafka =
            registry.findById(schema.reference(), ExtractorProtocol.KAFKA, uuid("kafka"));

    assertThat(resolvedUdp).contains(udp);
    assertThat(resolvedRtsp).contains(rtsp);
    assertThat(resolvedKafka).contains(kafka);
  }

  /** An identifier names one way of supplying one protocol's input, never another protocol's. */
  @Test
  void resolvesNoExtractorWhenTheIdentifierBelongsToAnotherProtocol() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP, TransportProtocol.UDP));
    var registry =
        new ConnectionExtractorRegistry(
            List.of(schema),
            List.of(
                tcpExtractor(schema, uuid("tcp")),
                extractor(schema, TransportProtocol.UDP, uuid("udp"))));

    assertThat(registry.findById(schema.reference(), ExtractorProtocol.TCP, uuid("udp"))).isEmpty();
  }

  @Test
  void resolvesNoExtractorRegisteredByAnotherSchema() {
    var selected = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP));
    var other = schema(SECOND_SCHEMA_ID, Set.of(TransportProtocol.TCP));
    var registry =
        new ConnectionExtractorRegistry(
            List.of(selected, other),
            List.of(tcpExtractor(selected, uuid("own")), tcpExtractor(other, uuid("foreign"))));

    assertThat(registry.findById(selected.reference(), ExtractorProtocol.TCP, uuid("foreign")))
        .isEmpty();
  }

  @Test
  void rejectsADeclaredTcpProtocolWithNoRegisteredExtractor() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP, TransportProtocol.UDP));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(extractor(schema, TransportProtocol.UDP, uuid("udp")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declares TCP but registers no extractor")
        .hasMessageContaining(FIRST_SCHEMA_ID)
        .hasMessageContaining("(Test)");
  }

  /** A protocol whose input the host still reads natively may be declared for its mappings. */
  @Test
  void acceptsAProtocolDeclaredForItsMappingsAloneWithNoExtractor() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP, TransportProtocol.RTSP));

    var registry = new ConnectionExtractorRegistry(List.of(schema), List.of());

    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.UDP)).isEmpty();
  }

  /**
   * A protocol's capability fixes the material its extractors produce, so a lookup typed by the
   * protocol can trust it. An extractor producing anything else must not start.
   */
  @ParameterizedTest
  @EnumSource(TransportProtocol.class)
  void rejectsAnExtractorThatDoesNotImplementItsProtocolsCapability(TransportProtocol protocol) {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(protocol));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema), List.of(unspecialized(schema, protocol, uuid("unshaped")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "must implement " + ExtractorProtocol.of(protocol).capability().getName())
        .hasMessageContaining(uuid("unshaped").toString());
  }

  /** One protocol's capability does not vouch for an extractor declaring another protocol. */
  @Test
  void rejectsAnExtractorDeclaringAProtocolOtherThanItsCapabilitys() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.RTSP));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        new UdpTestExtractor(
                            schema,
                            TransportProtocol.RTSP,
                            uuid("mislabelled"),
                            ConnectionExtractorRegistryTest::descriptor))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must implement " + RtspConnectionExtractorCapability.class.getName())
        .hasMessageContaining(uuid("mislabelled").toString());
  }

  @Test
  void rejectsAnExtractorForAProtocolItsSchemaDoesNotDeclare() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        tcpExtractor(schema, uuid("tcp")),
                        extractor(schema, TransportProtocol.RTSP, uuid("rtsp")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not declare RTSP")
        .hasMessageContaining(uuid("rtsp").toString())
        .hasMessageContaining(FIRST_SCHEMA_ID);
  }

  @Test
  void rejectsAnExtractorThatDeclaresNoProtocol() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of());

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema), List.of(unspecialized(schema, null, uuid("unprotocolled")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("protocol must not be null")
        .hasMessageContaining(uuid("unprotocolled").toString());
  }

  /** The identifier is unique within a schema version, so a stored selection is unambiguous. */
  @Test
  void rejectsOneIdentifierRegisteredForTwoProtocolsOfOneSchema() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.TCP, TransportProtocol.KAFKA));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        tcpExtractor(schema, uuid("shared")),
                        extractor(schema, TransportProtocol.KAFKA, uuid("shared")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate KAFKA extractor identifier")
        .hasMessageContaining(uuid("shared").toString());
  }

  @Test
  void rejectsAnExtractorOwnedByAnUnregisteredSchema() {
    var registered = schema(FIRST_SCHEMA_ID, Set.of());
    var orphan = schema(SECOND_SCHEMA_ID, Set.of(TransportProtocol.UDP));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(registered),
                    List.of(extractor(orphan, TransportProtocol.UDP, uuid("orphan")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unregistered connection schema for UDP extractor")
        .hasMessageContaining(uuid("orphan").toString())
        .hasMessageContaining(SECOND_SCHEMA_ID);
  }

  @Test
  void rejectsAMalformedDescriptorNamingItsProtocol() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));
    Supplier<ExtractorDescriptor> blankLabel =
        () -> new ExtractorDescriptor(UUID.randomUUID(), " ", List.of(field("endpoint")));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        extractor(schema, TransportProtocol.UDP, uuid("blank-label"), blankLabel))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid descriptor for UDP extractor")
        .cause()
        .hasMessageContaining("label must not be blank");
  }

  /** A client renders a field named like a standard one as that standard field. */
  @Test
  void rejectsAFieldThatTakesAStandardNameWithoutDeclaringTheStandardField() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));
    Supplier<ExtractorDescriptor> lookAlikeHost =
        () -> new ExtractorDescriptor(UUID.randomUUID(), "Look-alike", List.of(field("host")));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        extractor(
                            schema, TransportProtocol.UDP, uuid("look-alike"), lookAlikeHost))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field 'host' of UDP extractor")
        .hasMessageContaining(uuid("look-alike").toString())
        .hasMessageContaining("StandardExtractorField.HOST.declare(required)");
  }

  @Test
  void acceptsAStandardFieldDeclaredAsStandard() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));
    Supplier<ExtractorDescriptor> standardHost =
        () ->
            new ExtractorDescriptor(
                UUID.randomUUID(), "Standard", List.of(StandardExtractorField.HOST.declare(true)));
    var extractor =
        new UdpTestExtractor(schema, TransportProtocol.UDP, uuid("standard"), standardHost);

    var registry = new ConnectionExtractorRegistry(List.of(schema), List.of(extractor));

    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.UDP))
        .containsExactly(extractor);
  }

  @Test
  void rejectsTheSameSchemaVersionRegisteredTwice() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of());

    assertThatThrownBy(() -> new ConnectionExtractorRegistry(List.of(schema, schema), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate connection schema")
        .hasMessageContaining(FIRST_SCHEMA_ID);
  }

  @Test
  void rejectsAnExtractorWithoutAnIdentifier() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.UDP));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema), List.of(extractor(schema, TransportProtocol.UDP, null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Extractor identifier must not be null")
        .hasMessageContaining(FIRST_SCHEMA_ID);
  }

  @Test
  void rejectsAnExtractorThatSuppliesNoDescriptor() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of(TransportProtocol.KAFKA));

    assertThatThrownBy(
            () ->
                new ConnectionExtractorRegistry(
                    List.of(schema),
                    List.of(
                        extractor(
                            schema, TransportProtocol.KAFKA, uuid("undescribed"), () -> null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing descriptor for KAFKA extractor")
        .hasMessageContaining(uuid("undescribed").toString());
  }

  @Test
  void acceptsASchemaDeclaringNoProtocolAndRegisteringNoExtractor() {
    var schema = schema(FIRST_SCHEMA_ID, Set.of());

    var registry = new ConnectionExtractorRegistry(List.of(schema), List.of());

    assertThat(registry.extractorsFor(schema.reference(), ExtractorProtocol.TCP)).isEmpty();
  }

  private static IntegrationConnectionSchema schema(String id, Set<TransportProtocol> protocols) {
    return new TestConnectionSchema(UUID.fromString(id), 1, "Test", "Test integration", protocols);
  }

  private static ConnectionExtractorCapability<?, ?> extractor(
      IntegrationConnectionSchema schema, TransportProtocol protocol, UUID id) {
    return extractor(schema, protocol, id, ConnectionExtractorRegistryTest::descriptor);
  }

  /** Builds an extractor implementing the capability of the protocol it declares. */
  private static ConnectionExtractorCapability<?, ?> extractor(
      IntegrationConnectionSchema schema,
      TransportProtocol protocol,
      UUID id,
      Supplier<ExtractorDescriptor> descriptor) {
    return switch (protocol) {
      case TCP -> new TcpTestExtractor(schema, id);
      case UDP -> new UdpTestExtractor(schema, protocol, id, descriptor);
      case RTSP -> new RtspTestExtractor(schema, protocol, id, descriptor);
      case KAFKA -> new KafkaTestExtractor(schema, protocol, id, descriptor);
    };
  }

  private static UdpTestExtractor udpExtractor(IntegrationConnectionSchema schema, UUID id) {
    return new UdpTestExtractor(
        schema, TransportProtocol.UDP, id, ConnectionExtractorRegistryTest::descriptor);
  }

  private static RtspTestExtractor rtspExtractor(IntegrationConnectionSchema schema, UUID id) {
    return new RtspTestExtractor(
        schema, TransportProtocol.RTSP, id, ConnectionExtractorRegistryTest::descriptor);
  }

  private static KafkaTestExtractor kafkaExtractor(IntegrationConnectionSchema schema, UUID id) {
    return new KafkaTestExtractor(
        schema, TransportProtocol.KAFKA, id, ConnectionExtractorRegistryTest::descriptor);
  }

  private static UnspecializedExtractor unspecialized(
      IntegrationConnectionSchema schema, TransportProtocol protocol, UUID id) {
    return new UnspecializedExtractor(
        schema, protocol, id, ConnectionExtractorRegistryTest::descriptor);
  }

  private static TcpTestExtractor tcpExtractor(IntegrationConnectionSchema schema, UUID id) {
    return new TcpTestExtractor(schema, id);
  }

  /** Derives a stable UUID from a label, so a failure message still names the extractor. */
  private static UUID uuid(String label) {
    return UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8));
  }

  private static ExtractorDescriptor descriptor() {
    return new ExtractorDescriptor(UUID.randomUUID(), "Test extractor", List.of(field("endpoint")));
  }

  private static TextField field(String name) {
    return new TextField(name, "Test field", true);
  }

  /** An extractor that registry tests only index and look up, producing no material. */
  private interface InertExtractor<M> extends ConnectionExtractorCapability<ConnectionInput, M> {

    Supplier<ExtractorDescriptor> descriptorSupplier();

    @Override
    default ExtractorDescriptor descriptor() {
      return descriptorSupplier().get();
    }

    @Override
    default void validate(ConnectionInput input) {
      throw new AssertionError("Registry tests do not validate");
    }

    @Override
    default M extract(ConnectionInput input) {
      return null;
    }
  }

  /**
   * Implements only the generic capability, so its declared protocol does not fix its material.
   * This one produces a {@code String}, which no protocol's lookup may hand out.
   */
  private record UnspecializedExtractor(
      IntegrationConnectionSchema schema,
      TransportProtocol protocol,
      UUID id,
      Supplier<ExtractorDescriptor> descriptorSupplier)
      implements InertExtractor<String> {}

  private record UdpTestExtractor(
      IntegrationConnectionSchema schema,
      TransportProtocol protocol,
      UUID id,
      Supplier<ExtractorDescriptor> descriptorSupplier)
      implements UdpConnectionExtractorCapability, InertExtractor<UdpConnectionMaterial> {}

  private record RtspTestExtractor(
      IntegrationConnectionSchema schema,
      TransportProtocol protocol,
      UUID id,
      Supplier<ExtractorDescriptor> descriptorSupplier)
      implements RtspConnectionExtractorCapability, InertExtractor<RtspConnectionMaterial> {}

  private record KafkaTestExtractor(
      IntegrationConnectionSchema schema,
      TransportProtocol protocol,
      UUID id,
      Supplier<ExtractorDescriptor> descriptorSupplier)
      implements KafkaConnectionExtractorCapability, InertExtractor<KafkaConnectionMaterial> {}

  private record TcpTestExtractor(IntegrationConnectionSchema schema, UUID id)
      implements TcpConnectionExtractorCapability {

    @Override
    public ExtractorDescriptor descriptor() {
      return ConnectionExtractorRegistryTest.descriptor();
    }

    @Override
    public void validate(TcpConnectionInput input) {
      throw new AssertionError("Registry tests do not validate");
    }

    @Override
    public TcpConnectionMaterial extract(TcpConnectionInput input) {
      throw new AssertionError("Registry tests do not extract");
    }
  }
}
