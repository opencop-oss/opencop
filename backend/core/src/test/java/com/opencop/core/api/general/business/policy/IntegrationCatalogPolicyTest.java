// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogConflictException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogRegistration;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogSnapshot;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationCatalogPolicyTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("a54a565f-aa48-44d0-91c8-36550511376c");
  private static final IntegrationIdentity INTEGRATION =
      new IntegrationIdentity(INTEGRATION_ID, "Example", "Example integration", true);
  private static final UUID UDP_ID = UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31");
  private static final UUID ALPHA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID LOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID HIGH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final Set<TransportProtocol> TCP = Set.of(TransportProtocol.TCP);
  private static final Set<TransportProtocol> UDP = Set.of(TransportProtocol.UDP);

  @Test
  void aRegistrationWithDistinctIntegrationsAndSchemasIsAccepted() {
    var registration =
        new IntegrationCatalogRegistration(
            List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1), schema(INTEGRATION_ID, 2)));

    assertThatNoException()
        .isThrownBy(() -> IntegrationCatalogPolicy.requireConsistentRegistration(registration));
  }

  @Test
  void aSchemaVersionPublishedTwiceIsAConflict() {
    var registration =
        new IntegrationCatalogRegistration(
            List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1), schema(INTEGRATION_ID, 1)));

    assertThatThrownBy(() -> IntegrationCatalogPolicy.requireConsistentRegistration(registration))
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining("Duplicate registered connection schema")
        .hasMessageContaining(INTEGRATION_ID.toString())
        .hasMessageContaining("version 1");
  }

  @Test
  void anIntegrationPublishedTwiceIsAConflict() {
    var registration =
        new IntegrationCatalogRegistration(List.of(INTEGRATION, INTEGRATION), List.of());

    assertThatThrownBy(() -> IntegrationCatalogPolicy.requireConsistentRegistration(registration))
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining("Duplicate registered integration")
        .hasMessageContaining(INTEGRATION_ID.toString());
  }

  @Test
  void aSchemaOfAnIntegrationNoPluginPublishesIsAConflict() {
    var orphanId = UUID.fromString("0b7f6a1e-3c2d-4e5f-8a9b-1c2d3e4f5a6b");
    var registration =
        new IntegrationCatalogRegistration(List.of(INTEGRATION), List.of(schema(orphanId, 2)));

    assertThatThrownBy(() -> IntegrationCatalogPolicy.requireConsistentRegistration(registration))
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessage(
            "Connection schema "
                + orphanId
                + " version 2 belongs to an integration no plugin publishes");
  }

  @Test
  void changedDisplayMetadataIsRefreshedAndActivationKept() {
    var catalog = new IntegrationIdentity(INTEGRATION_ID, "Old", "Old description", false);

    assertThat(IntegrationCatalogPolicy.displayMetadataRefresh(INTEGRATION, catalog))
        .contains(new IntegrationIdentity(INTEGRATION_ID, "Example", "Example integration", false));
  }

  @Test
  void unchangedDisplayMetadataNeedsNoRefresh() {
    var catalog = new IntegrationIdentity(INTEGRATION_ID, "Example", "Example integration", false);

    assertThat(IntegrationCatalogPolicy.displayMetadataRefresh(INTEGRATION, catalog)).isEmpty();
  }

  @Test
  void displayRefreshRejectsAnotherIntegration() {
    var other = new IntegrationIdentity(UUID.randomUUID(), "Example", "Example integration", true);

    assertThatThrownBy(() -> IntegrationCatalogPolicy.displayMetadataRefresh(INTEGRATION, other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be the same integration");
  }

  // --- discovery -----------------------------------------------------------------------------

  @Test
  void anUnfilteredListingReportsALoadedVersionAsAvailable() {
    var catalog = snapshot(List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1)));

    var entries =
        IntegrationCatalogPolicy.discover(
            catalog, Map.of(schema(INTEGRATION_ID, 1), TCP), Optional.empty());

    assertThat(entries)
        .containsExactly(
            new IntegrationCatalogEntry(INTEGRATION_ID, 1, "Example", "Example integration", true));
  }

  @Test
  void anUnfilteredListingReportsAVersionWithNoLoadedSchemaAsUnavailable() {
    var catalog = snapshot(List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1)));

    var entries = IntegrationCatalogPolicy.discover(catalog, Map.of(), Optional.empty());

    assertThat(entries)
        .singleElement()
        .extracting(IntegrationCatalogEntry::available)
        .isEqualTo(false);
  }

  @Test
  void aProtocolFilteredListingOmitsAVersionWithNoLoadedSchema() {
    var catalog = snapshot(List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1)));

    assertThat(
            IntegrationCatalogPolicy.discover(
                catalog, Map.of(), Optional.of(TransportProtocol.TCP)))
        .isEmpty();
  }

  @Test
  void aProtocolFilterKeepsOnlyVersionsDeclaringThatProtocol() {
    var udp = new IntegrationIdentity(UDP_ID, "Datagram", "Datagram integration", true);
    var catalog =
        snapshot(List.of(INTEGRATION, udp), List.of(schema(INTEGRATION_ID, 1), schema(UDP_ID, 1)));
    var declared = Map.of(schema(INTEGRATION_ID, 1), TCP, schema(UDP_ID, 1), UDP);

    assertThat(
            IntegrationCatalogPolicy.discover(
                catalog, declared, Optional.of(TransportProtocol.TCP)))
        .extracting(IntegrationCatalogEntry::integrationId)
        .containsExactly(INTEGRATION_ID);
    assertThat(
            IntegrationCatalogPolicy.discover(
                catalog, declared, Optional.of(TransportProtocol.UDP)))
        .extracting(IntegrationCatalogEntry::integrationId)
        .containsExactly(UDP_ID);
  }

  @Test
  void aVersionDeclaringSeveralProtocolsAppearsUnderEachOfThem() {
    var both = Set.of(TransportProtocol.TCP, TransportProtocol.UDP);
    var catalog = snapshot(List.of(INTEGRATION), List.of(schema(INTEGRATION_ID, 1)));
    var declared = Map.of(schema(INTEGRATION_ID, 1), both);

    // Iterates what the version declares, not every constant: a protocol added to the SDK enum is
    // not one this fixture declares, and would fail here for the wrong reason.
    for (var protocol : both) {
      assertThat(IntegrationCatalogPolicy.discover(catalog, declared, Optional.of(protocol)))
          .extracting(IntegrationCatalogEntry::integrationId)
          .containsExactly(INTEGRATION_ID);
    }
  }

  @Test
  void aDeactivatedIntegrationWithholdsEveryVersionFromFilteredAndUnfilteredListingsAlike() {
    var deactivated =
        new IntegrationIdentity(INTEGRATION_ID, "Example", "Example integration", false);
    var catalog =
        snapshot(
            List.of(deactivated), List.of(schema(INTEGRATION_ID, 1), schema(INTEGRATION_ID, 2)));
    var declared = Map.of(schema(INTEGRATION_ID, 1), TCP, schema(INTEGRATION_ID, 2), TCP);

    assertThat(IntegrationCatalogPolicy.discover(catalog, declared, Optional.empty())).isEmpty();
    assertThat(
            IntegrationCatalogPolicy.discover(
                catalog, declared, Optional.of(TransportProtocol.TCP)))
        .isEmpty();
  }

  @Test
  void anIntegrationOfferingNoConnectionContractHasNoEntries() {
    var catalog = snapshot(List.of(INTEGRATION), List.of());

    assertThat(IntegrationCatalogPolicy.discover(catalog, Map.of(), Optional.empty())).isEmpty();
  }

  @Test
  void aVersionWhoseIntegrationTheSnapshotDoesNotCarryIsSkipped() {
    var catalog = snapshot(List.of(), List.of(schema(INTEGRATION_ID, 1)));

    assertThat(IntegrationCatalogPolicy.discover(catalog, Map.of(), Optional.empty())).isEmpty();
  }

  @Test
  void resultsAreOrderedByNameThenIntegrationThenSchemaVersionWhateverTheInputOrder() {
    var low = new IntegrationIdentity(LOW_ID, "Shared name", "Shared", true);
    var high = new IntegrationIdentity(HIGH_ID, "Shared name", "Shared", true);
    var alpha = new IntegrationIdentity(ALPHA_ID, "Alpha", "Alpha", true);
    var schemas =
        new ArrayList<>(
            List.of(schema(HIGH_ID, 1), schema(LOW_ID, 2), schema(LOW_ID, 1), schema(ALPHA_ID, 1)));
    Collections.shuffle(schemas, new Random(20260930L));

    var entries =
        IntegrationCatalogPolicy.discover(
            snapshot(List.of(high, alpha, low), schemas), Map.of(), Optional.empty());

    assertThat(entries)
        .extracting(
            IntegrationCatalogEntry::name,
            IntegrationCatalogEntry::integrationId,
            IntegrationCatalogEntry::schemaVersion)
        .containsExactly(
            tuple("Alpha", ALPHA_ID, 1),
            tuple("Shared name", LOW_ID, 1),
            tuple("Shared name", LOW_ID, 2),
            tuple("Shared name", HIGH_ID, 1));
  }

  /**
   * {@code 7f...} sorts before {@code 80...} as canonical text but after it under {@link
   * UUID#compareTo}, whose most significant bits are compared as a signed long. A client can only
   * reproduce the published order if the text order wins.
   */
  @Test
  void nameTiesBreakOnCanonicalIdentifierTextRatherThanSignedUuidOrder() {
    var textFirst = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
    var textSecond = UUID.fromString("80000000-0000-4000-8000-000000000000");
    var rows = List.of(schema(textSecond, 1), schema(textFirst, 1));
    var integrations =
        List.of(
            new IntegrationIdentity(textSecond, "Shared name", "Shared", true),
            new IntegrationIdentity(textFirst, "Shared name", "Shared", true));

    assertThat(
            IntegrationCatalogPolicy.discover(
                snapshot(integrations, rows), Map.of(), Optional.empty()))
        .extracting(IntegrationCatalogEntry::integrationId)
        .containsExactly(textFirst, textSecond);
  }

  // --- protocol parsing ----------------------------------------------------------------------

  @Test
  void parseProtocolTreatsAMissingParameterAsTheUnfilteredCatalog() {
    assertThat(IntegrationCatalogPolicy.parseProtocol(null)).isEmpty();
  }

  @Test
  void parseProtocolAcceptsAnyCasingAndSurroundingWhitespace() {
    assertThat(IntegrationCatalogPolicy.parseProtocol("tcp")).contains(TransportProtocol.TCP);
    assertThat(IntegrationCatalogPolicy.parseProtocol("  UdP  ")).contains(TransportProtocol.UDP);
  }

  @Test
  void parseProtocolRejectsAnUnknownValueAndNamesEveryAcceptedOne() {
    assertThatThrownBy(() -> IntegrationCatalogPolicy.parseProtocol("SCTP"))
        .isInstanceOf(UnknownTransportProtocolException.class)
        .hasMessageContaining("SCTP")
        .hasMessageContaining("TCP")
        .hasMessageContaining("UDP");
  }

  @Test
  void parseProtocolRejectsABlankValueRatherThanIgnoringTheParameter() {
    assertThatThrownBy(() -> IntegrationCatalogPolicy.parseProtocol(""))
        .isInstanceOf(UnknownTransportProtocolException.class);
    assertThatThrownBy(() -> IntegrationCatalogPolicy.parseProtocol("   "))
        .isInstanceOf(UnknownTransportProtocolException.class);
  }

  private static IntegrationCatalogSnapshot snapshot(
      List<IntegrationIdentity> integrations, List<IntegrationConnectionSchemaKey> schemas) {
    return new IntegrationCatalogSnapshot(integrations, schemas);
  }

  private static IntegrationConnectionSchemaKey schema(UUID integrationId, int version) {
    return new IntegrationConnectionSchemaKey(integrationId, version);
  }
}
