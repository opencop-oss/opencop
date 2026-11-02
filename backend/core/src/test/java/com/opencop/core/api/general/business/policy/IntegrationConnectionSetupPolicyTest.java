// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupOutcome;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IntegrationConnectionSetupPolicyTest {

  private static final IntegrationConnectionSchemaKey KEY =
      new IntegrationConnectionSchemaKey(
          UUID.fromString("1ce6c2c6-2f29-4a5f-9f6e-3f9a2f7f5b21"), 3);

  private static final ExtractorDescriptor EXTRACTOR =
      new ExtractorDescriptor(
          UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
          "Endpoint",
          List.of(StandardExtractorField.HOST.declare(true)));
  private static final ProtocolConnectionSetup TCP =
      new ProtocolConnectionSetup.Tcp(
          List.of(EXTRACTOR),
          MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming()),
          List.of(),
          List.of(AccessMode.READ));
  private static final ProtocolConnectionSetup UDP =
      new ProtocolConnectionSetup.Udp(
          List.of(EXTRACTOR), MapperDeclaration.openMapper(), List.of(AccessMode.READ));

  @Test
  void describesEveryProtocolItIsGiven() {
    var setup = described(List.of(TCP, UDP));

    assertThat(setup.schema()).isEqualTo(KEY);
    assertThat(setup.protocols()).containsExactly(TCP, UDP);
  }

  @Test
  void reportsUnknownWhenTheCatalogCarriesNoSuchSchemaVersion() {
    var outcome =
        IntegrationConnectionSetupPolicy.describe(
            KEY, Optional.empty(), Optional.of(schema()), List.of(TCP));

    assertThat(reasonOf(outcome)).isEqualTo(IntegrationConnectionSetupRejection.UNKNOWN);
  }

  @Test
  void reportsDeactivationAheadOfUnavailabilitySoEveryRuntimeAnswersAlike() {
    var outcome =
        IntegrationConnectionSetupPolicy.describe(
            KEY, Optional.of(owner(false)), Optional.empty(), List.of());

    assertThat(reasonOf(outcome)).isEqualTo(IntegrationConnectionSetupRejection.INACTIVE);
  }

  @Test
  void reportsUnavailableWhenThisRuntimeRegistersNoImplementation() {
    var outcome =
        IntegrationConnectionSetupPolicy.describe(
            KEY, Optional.of(owner(true)), Optional.empty(), List.of());

    assertThat(reasonOf(outcome)).isEqualTo(IntegrationConnectionSetupRejection.UNAVAILABLE);
  }

  @Test
  void reportsProtocolUnsupportedWhenNoProtocolOffersASetup() {
    var outcome =
        IntegrationConnectionSetupPolicy.describe(
            KEY, Optional.of(owner(true)), Optional.of(schema()), List.of());

    assertThat(reasonOf(outcome))
        .isEqualTo(IntegrationConnectionSetupRejection.PROTOCOL_UNSUPPORTED);
  }

  @Test
  void givesEveryRejectionReasonItsOwnNonSensitiveDiagnostic() {
    var reasons = IntegrationConnectionSetupRejection.values();

    assertThat(Stream.of(reasons).map(IntegrationConnectionSetupRejection::code))
        .doesNotHaveDuplicates();
    assertThat(Stream.of(reasons).map(IntegrationConnectionSetupRejection::message))
        .doesNotHaveDuplicates()
        .allSatisfy(message -> assertThat(message).isNotBlank());
  }

  private static IntegrationConnectionSetup described(List<ProtocolConnectionSetup> protocols) {
    var outcome =
        IntegrationConnectionSetupPolicy.describe(
            KEY, Optional.of(owner(true)), Optional.of(schema()), protocols);
    assertThat(outcome).isInstanceOf(IntegrationConnectionSetupOutcome.Described.class);
    return ((IntegrationConnectionSetupOutcome.Described) outcome).setup();
  }

  private static IntegrationConnectionSetupRejection reasonOf(
      IntegrationConnectionSetupOutcome outcome) {
    assertThat(outcome).isInstanceOf(IntegrationConnectionSetupOutcome.NotDescribable.class);
    return ((IntegrationConnectionSetupOutcome.NotDescribable) outcome).reason();
  }

  private static IntegrationIdentity owner(boolean active) {
    return new IntegrationIdentity(KEY.integrationId(), "Name", "Description", active);
  }

  private static IntegrationConnectionSchema schema() {
    return new TestConnectionSchema(
        KEY.integrationId(),
        KEY.schemaVersion(),
        "Name",
        "Description",
        Set.of(TransportProtocol.TCP, TransportProtocol.UDP));
  }
}
