// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.IntegrationAssignment;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.LifecycleUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.Rejection;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason;
import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PluginMapperCatalogReconciliationPolicyTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000aa");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000bb");

  @Test
  void acceptsUuidHistoryThisIntegrationOwns() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", "Provider description");

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(
            contribution,
            List.of(
                mapper(mapperId, "Provider name", null, false, INTEGRATION_ID, 1, false, false),
                mapper(mapperId, "Provider name", null, false, INTEGRATION_ID, 2, false, false)));

    assertThat(collision).isEmpty();
  }

  @Test
  void acceptsUuidHistoryAnotherIntegrationOwns() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);
    var otherOwner =
        mapper(mapperId, "Provider name", null, false, OTHER_INTEGRATION_ID, 1, false, false);

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(contribution, List.of(otherOwner));

    assertThat(collision).isEmpty();
  }

  @Test
  void rejectsAnEditableUuidOwner() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);
    var editable = mapper(mapperId, "User mapper", null, true, null, 1, false, false);

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(contribution, List.of(editable));

    assertThat(collision)
        .contains(
            new Rejection(
                mapperId, "Provider name", RejectionReason.EDITABLE_UUID_OWNER, mapperId));
  }

  @Test
  void rejectsADataAuthoredUuidOwner() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);
    var dataAuthored = mapper(mapperId, "Data-authored mapper", null, false, null, 1, true, false);

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(contribution, List.of(dataAuthored));

    assertThat(collision)
        .contains(
            new Rejection(
                mapperId, "Provider name", RejectionReason.DATA_AUTHORED_UUID_OWNER, mapperId));
  }

  @Test
  void editableOwnershipTakesPriorityAcrossIntegrationOwnedHistory() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);
    var pluginDataAuthoredHead =
        mapper(mapperId, "Current plugin mapper", null, false, INTEGRATION_ID, 2, true, false);
    var editableHistory =
        mapper(mapperId, "Historical user mapper", null, true, INTEGRATION_ID, 1, false, false);

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(
            contribution, List.of(pluginDataAuthoredHead, editableHistory));

    assertThat(collision)
        .get()
        .extracting(Rejection::reason)
        .isEqualTo(RejectionReason.EDITABLE_UUID_OWNER);
  }

  @Test
  void rejectsANonEditableUuidNoIntegrationOwns() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);

    var collision =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(
            contribution, List.of(mapper(mapperId, "System-owned mapper")));

    assertThat(collision)
        .get()
        .extracting(Rejection::reason)
        .isEqualTo(RejectionReason.CATALOG_UUID_OWNER);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsANameOwnedByAnotherPersistedMapper(boolean archived) {
    var ownerId = UUID.randomUUID();
    var contributionId = UUID.randomUUID();
    var contribution = decoder(contributionId, "Reserved name", null);
    var owner = mapper(ownerId, "Reserved name", null, false, null, 1, false, archived);

    var collision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            contribution, List.of(owner), List.of(), 1);

    assertThat(collision)
        .contains(
            new Rejection(
                contributionId, "Reserved name", RejectionReason.PERSISTED_NAME_OWNER, ownerId));
  }

  @Test
  void acceptsANameAlreadyPersistedByTheSameUuid() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);

    var collision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            contribution, List.of(mapper(mapperId, "Provider name")), List.of(mapperId), 0);

    assertThat(collision).isEmpty();
  }

  @Test
  void rejectsEveryNewClaimantOfOneUnownedName() {
    var firstId = UUID.randomUUID();
    var secondId = UUID.randomUUID();

    var firstCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(firstId, "Duplicate", null), List.of(), List.of(), 2);
    var secondCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(secondId, "Duplicate", null), List.of(), List.of(), 2);

    assertThat(firstCollision)
        .contains(
            new Rejection(firstId, "Duplicate", RejectionReason.DUPLICATE_UNOWNED_NAME, null));
    assertThat(secondCollision)
        .contains(
            new Rejection(secondId, "Duplicate", RejectionReason.DUPLICATE_UNOWNED_NAME, null));
  }

  @Test
  void acceptsASingleNewClaimantOfAnUnownedName() {
    var contribution = decoder(UUID.randomUUID(), "Unowned name", null);

    var collision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            contribution, List.of(), List.of(), 1);

    assertThat(collision).isEmpty();
  }

  @Test
  void existingIdentityWinsAnUnownedNameClaimAgainstNewIdentities() {
    var existingId = UUID.randomUUID();
    var newId = UUID.randomUUID();
    var existingClaimants = List.of(existingId);

    var existingCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(existingId, "Shared name", null), List.of(), existingClaimants, 2);
    var newCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(newId, "Shared name", null), List.of(), existingClaimants, 2);

    assertThat(existingCollision).isEmpty();
    assertThat(newCollision)
        .contains(
            new Rejection(
                newId, "Shared name", RejectionReason.DUPLICATE_UNOWNED_NAME, existingId));
  }

  @Test
  void rejectsMultipleExistingClaimantsAndReportsAnotherIdentityDeterministically() {
    var firstId = UUID.fromString("1607544e-6e4e-4036-be4c-46bf0764c00a");
    var secondId = UUID.fromString("1607544e-6e4e-4036-be4c-46bf0764c00b");
    var claimantIds = List.of(secondId, firstId);

    var firstCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(firstId, "Shared name", null), List.of(), claimantIds, 0);
    var secondCollision =
        PluginMapperCatalogReconciliationPolicy.nameCollision(
            decoder(secondId, "Shared name", null), List.of(), claimantIds, 0);

    assertThat(firstCollision)
        .contains(
            new Rejection(
                firstId, "Shared name", RejectionReason.DUPLICATE_UNOWNED_NAME, secondId));
    assertThat(secondCollision)
        .contains(
            new Rejection(
                secondId, "Shared name", RejectionReason.DUPLICATE_UNOWNED_NAME, firstId));
  }

  @Test
  void detectsProviderNameChanges() {
    var mapperId = UUID.randomUUID();

    assertThat(
            PluginMapperCatalogReconciliationPolicy.metadataRequiresReconciliation(
                mapper(mapperId, "Old name"), decoder(mapperId, "New name", null)))
        .isTrue();
  }

  @Test
  void detectsProviderDescriptionChanges() {
    var mapperId = UUID.randomUUID();
    var existing =
        mapper(mapperId, "Provider name", "Old description", false, null, 1, false, false);

    assertThat(
            PluginMapperCatalogReconciliationPolicy.metadataRequiresReconciliation(
                existing, decoder(mapperId, "Provider name", "New description")))
        .isTrue();
  }

  @Test
  void acceptsMatchingProviderMetadata() {
    var mapperId = UUID.randomUUID();
    var existing =
        mapper(mapperId, "Provider name", "Provider description", false, null, 1, false, false);

    assertThat(
            PluginMapperCatalogReconciliationPolicy.metadataRequiresReconciliation(
                existing, decoder(mapperId, "Provider name", "Provider description")))
        .isFalse();
  }

  @Test
  void archivesAnActiveIntegrationOwnedMapperWithoutAnAcceptedContribution() {
    var mapperId = UUID.randomUUID();
    var activePlugin =
        mapper(mapperId, "Provider name", null, false, INTEGRATION_ID, 1, false, false);

    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(activePlugin, false))
        .contains(new LifecycleUpdate(mapperId, true));
  }

  @Test
  void reenablesAnArchivedIntegrationOwnedMapperWithAnAcceptedContribution() {
    var mapperId = UUID.randomUUID();
    var archivedPlugin =
        mapper(mapperId, "Provider name", null, false, INTEGRATION_ID, 1, false, true);

    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(archivedPlugin, true))
        .contains(new LifecycleUpdate(mapperId, false));
  }

  @Test
  void leavesMappersNoIntegrationOwnsAndAlreadyDesiredLifecycleStatesUnchanged() {
    var mapperId = UUID.randomUUID();
    var userOwned = mapper(mapperId, "User mapper", null, true, null, 1, false, false);
    var unownedArchived = mapper(mapperId, "Catalog mapper", null, false, null, 1, false, true);
    var activePlugin =
        mapper(mapperId, "Provider mapper", null, false, INTEGRATION_ID, 1, false, false);
    var archivedPlugin =
        mapper(mapperId, "Provider mapper", null, false, INTEGRATION_ID, 1, false, true);

    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(userOwned, false)).isEmpty();
    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(unownedArchived, true))
        .isEmpty();
    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(activePlugin, true))
        .isEmpty();
    assertThat(PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(archivedPlugin, false))
        .isEmpty();
  }

  @Test
  void rejectsAContributionWhoseIdentifierTheOtherDirectionAlreadyHolds() {
    var mapperId = UUID.randomUUID();
    var egressContribution =
        new PluginMapperContribution(
            mapperId, "Provider name", null, 1, MapperQualifier.EGRESS, INTEGRATION_ID);

    var rejection =
        PluginMapperCatalogReconciliationPolicy.uuidCollision(
            egressContribution, List.of(mapper(mapperId, "Provider name")));

    assertThat(rejection)
        .get()
        .extracting(Rejection::reason)
        .isEqualTo(RejectionReason.QUALIFIER_UUID_OWNER);
  }

  @Test
  void assignsTheContributingIntegrationToANewOrDifferentlyOwnedEntryOnly() {
    var mapperId = UUID.randomUUID();
    var contribution = decoder(mapperId, "Provider name", null);
    var otherOwned =
        mapper(mapperId, "Provider name", null, false, OTHER_INTEGRATION_ID, 1, false, false);
    var owned = mapper(mapperId, "Provider name", null, false, INTEGRATION_ID, 1, false, false);

    assertThat(
            PluginMapperCatalogReconciliationPolicy.integrationAssignment(
                Optional.empty(), contribution))
        .contains(new IntegrationAssignment(mapperId, INTEGRATION_ID));
    assertThat(
            PluginMapperCatalogReconciliationPolicy.integrationAssignment(
                Optional.of(otherOwned), contribution))
        .contains(new IntegrationAssignment(mapperId, INTEGRATION_ID));
    assertThat(
            PluginMapperCatalogReconciliationPolicy.integrationAssignment(
                Optional.of(owned), contribution))
        .isEmpty();
  }

  private static PluginMapperContribution decoder(UUID mapperId, String name, String description) {
    return new PluginMapperContribution(
        mapperId, name, description, 1, MapperQualifier.INGRESS, INTEGRATION_ID);
  }

  private static MapperCatalogProfile mapper(UUID mapperId, String name) {
    return mapper(mapperId, name, null, false, null, 1, false, false);
  }

  private static MapperCatalogProfile mapper(
      UUID mapperId,
      String name,
      String description,
      boolean editable,
      UUID integrationId,
      int version,
      boolean mappingDefinitionPresent,
      boolean archived) {
    return new MapperCatalogProfile(
        mapperId,
        version,
        name,
        description,
        editable,
        mappingDefinitionPresent,
        archived,
        MapperQualifier.INGRESS,
        integrationId);
  }
}
