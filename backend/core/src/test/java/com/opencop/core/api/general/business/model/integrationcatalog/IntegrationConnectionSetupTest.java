// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the order the view publishes.
 *
 * <p>Every identifier here is chosen so that canonical-text order and {@link UUID#compareTo} order
 * disagree: {@code HIGH} has its high bit set, so natural order places it first while text order
 * places it last. Identifiers that all begin with the same zero block cannot tell the two apart.
 */
class IntegrationConnectionSetupTest {

  private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID MIDDLE = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
  private static final UUID HIGH = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

  private static final IntegrationConnectionSchemaKey SCHEMA =
      new IntegrationConnectionSchemaKey(
          UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31"), 2);
  private static final ExtractorDescriptor EXTRACTOR =
      new ExtractorDescriptor(LOW, "Only", List.of(StandardExtractorField.HOST.declare(true)));

  @Test
  void ordersProtocolsByDeclarationOrderWhateverOrderTheyWereSupplied() {
    var setup =
        new IntegrationConnectionSetup(
            SCHEMA,
            List.of(
                new ProtocolConnectionSetup.Kafka(
                    List.of(EXTRACTOR), MapperDeclaration.openMapper(), List.of(AccessMode.READ)),
                new ProtocolConnectionSetup.Udp(
                    List.of(EXTRACTOR), MapperDeclaration.openMapper(), List.of(AccessMode.READ)),
                tcp(List.of())));

    assertThat(setup.protocols())
        .extracting(ProtocolConnectionSetup::protocol)
        .containsExactly(TransportProtocol.TCP, TransportProtocol.UDP, TransportProtocol.KAFKA);
  }

  @Test
  void rejectsASetupOfferingNoProtocol() {
    assertThatThrownBy(() -> new IntegrationConnectionSetup(SCHEMA, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAProtocolOfferingNoExtractor() {
    assertThatThrownBy(
            () ->
                new ProtocolConnectionSetup.Udp(
                    List.of(), MapperDeclaration.openMapper(), List.of(AccessMode.READ)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ordersResolvedFramingsInCanonicalTextOrder() {
    var setup = tcp(List.of(framing(HIGH), framing(LOW), framing(MIDDLE)));

    assertThat(setup.framings())
        .extracting(ConnectionFraming::id)
        .containsExactly(LOW, MIDDLE, HIGH);
  }

  @Test
  void publishesAnOrderAClientCanReproduceBySortingTheTextItReceived() {
    var setup = tcp(List.of(framing(HIGH), framing(LOW), framing(MIDDLE)));

    var asPublished =
        setup.framings().stream().map(ConnectionFraming::id).map(UUID::toString).toList();

    assertThat(asPublished).isSorted();
  }

  private static ConnectionFraming framing(UUID id) {
    return new ConnectionFraming(id, DelimiterModes.DELIMITER_INCLUDE);
  }

  private static ProtocolConnectionSetup.Tcp tcp(List<ConnectionFraming> framings) {
    return new ProtocolConnectionSetup.Tcp(
        List.of(EXTRACTOR),
        MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming()),
        framings,
        List.of(AccessMode.READ));
  }
}
