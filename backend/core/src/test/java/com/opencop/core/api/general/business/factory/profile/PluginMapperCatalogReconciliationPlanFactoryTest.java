// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import static com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason.CATALOG_UUID_OWNER;
import static com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason.DATA_AUTHORED_UUID_OWNER;
import static com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason.DUPLICATE_UNOWNED_NAME;
import static com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason.EDITABLE_UUID_OWNER;
import static com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason.PERSISTED_NAME_OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.IntegrationAssignment;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.LifecycleUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.MetadataUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.Rejection;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason;
import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class PluginMapperCatalogReconciliationPlanFactoryTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000bb");

  @Test
  void plansAnOrdinaryPluginMapperInsertionForAMissingIdentity() {
    var mapperId = UUID.randomUUID();

    var plan = createPlan(new TestDecoder(mapperId), List.of());

    assertThat(plan.insertions())
        .singleElement()
        .satisfies(
            mapper -> {
              assertThat(mapper.id()).isEqualTo(mapperId);
              assertThat(mapper.name()).isEqualTo("Contributed mapper");
              assertThat(mapper.description()).isEqualTo("Provider-declared description");
              assertThat(mapper.version()).isEqualTo(7);
              assertThat(mapper.editable()).isFalse();
              assertThat(mapper.archived()).isFalse();
              assertThat(mapper.dynamicMapper()).isNull();
            });
    assertThat(plan.integrationAssignments())
        .containsExactly(new IntegrationAssignment(mapperId, TEST_INTEGRATION.id()));
    assertThat(plan.metadataUpdates()).isEmpty();
    assertThat(plan.lifecycleUpdates()).isEmpty();
    assertThat(plan.rejections()).isEmpty();
  }

  @Test
  void plansProviderMetadataForAnExistingIntegrationOwnedMapper() {
    var mapperId = UUID.randomUUID();
    var existing = pluginMapper(mapperId, "Old name", "Old description", 3, false);

    var plan = createPlan(new TestDecoder(mapperId), List.of(existing));

    assertThat(plan.insertions()).isEmpty();
    assertThat(plan.metadataUpdates())
        .containsExactly(
            new MetadataUpdate(mapperId, "Contributed mapper", "Provider-declared description"));
  }

  @Test
  void selectsTheLatestCatalogVersionRegardlessOfLoadOrder() {
    var mapperId = UUID.randomUUID();
    var historical = pluginMapper(mapperId, "Historical name", "Historical description", 1, false);
    var latest =
        pluginMapper(mapperId, "Contributed mapper", "Provider-declared description", 3, false);
    var contribution = new TestDecoder(mapperId);

    var historicalFirst = createPlan(contribution, List.of(historical, latest));
    var latestFirst = createPlan(contribution, List.of(latest, historical));

    assertThat(historicalFirst).usingRecursiveComparison().isEqualTo(latestFirst);
    assertThat(historicalFirst.metadataUpdates()).isEmpty();
    assertThat(historicalFirst.insertions()).isEmpty();
  }

  @Test
  void rejectsAnIdentifierAMapperNoIntegrationOwnsHoldsWithoutTouchingIt() {
    var mapperId = UUID.randomUUID();
    var unowned = catalogMapper(mapperId, "COT", false, 3, false);
    var contribution = new TestDecoder(mapperId, "Provider COT", "Provider description", 7);

    var plan = createPlan(contribution, List.of(unowned));

    assertThat(plan.rejections())
        .containsExactly(new Rejection(mapperId, "Provider COT", CATALOG_UUID_OWNER, mapperId));
    assertThat(plan.insertions()).isEmpty();
    assertThat(plan.metadataUpdates()).isEmpty();
    assertThat(plan.integrationAssignments()).isEmpty();
    assertThat(plan.lifecycleUpdates()).isEmpty();
  }

  @Test
  void rejectsUuidOwnersAndStillPlansAnUnrelatedInsertion() {
    var editableId = UUID.randomUUID();
    var dataAuthoredId = UUID.randomUUID();
    var unrelatedId = UUID.randomUUID();
    var catalog =
        List.of(
            catalogMapper(editableId, "Editable owner", false, 1, true),
            catalogMapper(dataAuthoredId, "Data owner", true, 1, false));
    var contributions =
        List.of(
            new TestDecoder(editableId, "Editable claim", null, 1),
            new TestDecoder(dataAuthoredId, "Data claim", null, 1),
            new TestDecoder(unrelatedId, "Unrelated", null, 1));

    var plan = createPlan(contributions, catalog);

    assertThat(plan.rejections())
        .extracting(Rejection::reason)
        .containsExactlyInAnyOrder(EDITABLE_UUID_OWNER, DATA_AUTHORED_UUID_OWNER);
    assertThat(plan.insertions()).extracting(MapperProfile::id).containsExactly(unrelatedId);
  }

  @Test
  void rejectsEveryDuplicateUnownedNameWithoutBlockingAnUnrelatedInsertion() {
    var firstId = UUID.randomUUID();
    var secondId = UUID.randomUUID();
    var unrelatedId = UUID.randomUUID();
    var contributions =
        List.of(
            new TestDecoder(firstId, "Duplicate name", null, 1),
            new TestDecoder(secondId, "Duplicate name", null, 1),
            new TestDecoder(unrelatedId, "Unrelated", null, 1));

    var plan = createPlan(contributions, List.of());

    assertThat(plan.rejections())
        .filteredOn(rejection -> rejection.reason() == DUPLICATE_UNOWNED_NAME)
        .extracting(Rejection::mapperId)
        .containsExactlyInAnyOrder(firstId, secondId);
    assertThat(plan.insertions()).extracting(MapperProfile::id).containsExactly(unrelatedId);
  }

  @Test
  void archivesAPluginMapperWhoseContributionCollidesWithAPersistedNameOwner() {
    var pluginId = UUID.randomUUID();
    var ownerId = UUID.randomUUID();
    var pluginMapper = pluginMapper(pluginId, "Old provider name", null, 7, false);
    var nameOwner = catalogMapper(ownerId, "Reserved name", false, 1, true);
    var contribution = new TestDecoder(pluginId, "Reserved name", null, 1);

    var plan = createPlan(contribution, List.of(pluginMapper, nameOwner));

    assertThat(plan.rejections())
        .containsExactly(new Rejection(pluginId, "Reserved name", PERSISTED_NAME_OWNER, ownerId));
    assertThat(plan.lifecycleUpdates()).containsExactly(new LifecycleUpdate(pluginId, true));
    assertThat(plan.metadataUpdates()).isEmpty();
  }

  @Test
  void existingPluginIdentityWinsAnUnownedNameClaimAgainstNewContributions() {
    var firstNewId = UUID.fromString("1607544e-6e4e-4036-be4c-46bf0764c00a");
    var existingId = UUID.fromString("1607544e-6e4e-4036-be4c-46bf0764c00b");
    var secondNewId = UUID.fromString("1607544e-6e4e-4036-be4c-46bf0764c00c");
    var existing = pluginMapper(existingId, "Old provider name", "Old description", 2, false);
    var contributions =
        List.of(
            new TestDecoder(secondNewId, "New provider name", null, 1),
            new TestDecoder(existingId, "New provider name", "Current description", 3),
            new TestDecoder(firstNewId, "New provider name", null, 1));

    var plan = createPlan(contributions, List.of(existing));

    assertThat(plan.metadataUpdates())
        .containsExactly(
            new MetadataUpdate(existingId, "New provider name", "Current description"));
    assertThat(plan.rejections())
        .extracting(Rejection::mapperId)
        .containsExactly(firstNewId, secondNewId);
    assertThat(plan.insertions()).isEmpty();
  }

  @Test
  void plansMissingContributionArchiveAndReturningContributionReenable() {
    var missingId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    var returningId = UUID.fromString("00000000-0000-4000-8000-000000000002");
    var active = pluginMapper(missingId, "Missing mapper", null, 1, false);
    var archived = pluginMapper(returningId, "Returning mapper", null, 1, true);
    var returningContribution = new TestDecoder(returningId, "Returning mapper", null, 1);

    var plan = createPlan(returningContribution, List.of(archived, active));

    assertThat(plan.lifecycleUpdates())
        .containsExactly(
            new LifecycleUpdate(missingId, true), new LifecycleUpdate(returningId, false));
  }

  @Test
  void leavesMatchingPluginAndUserOwnedLifecycleStatesUntouched() {
    var pluginId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var activePlugin = pluginMapper(pluginId, "Contributed mapper", null, 1, false);
    var userMapper = catalogMapper(userId, "User mapper", false, 1, true);

    var plan =
        createPlan(
            new TestDecoder(pluginId, "Contributed mapper", null, 1),
            List.of(activePlugin, userMapper));

    assertThat(plan.lifecycleUpdates()).isEmpty();
  }

  @Test
  void anEgressPlanLeavesIngressEntriesAloneAndArchivesEgressEntriesNoLongerContributed() {
    var ingressPlugin = pluginMapper(UUID.randomUUID(), "COT", null, 1, false);
    var retiredEgress =
        new MapperCatalogProfile(
            UUID.randomUUID(),
            1,
            "Retired egress",
            null,
            false,
            false,
            false,
            MapperQualifier.EGRESS,
            TEST_INTEGRATION.id());

    var plan =
        PluginMapperCatalogReconciliationPlanFactory.create(
            MapperQualifier.EGRESS, List.of(), List.of(ingressPlugin, retiredEgress));

    assertThat(plan.lifecycleUpdates())
        .containsExactly(new LifecycleUpdate(retiredEgress.id(), true));
  }

  @Test
  void aNewEgressContributionIsInsertedAsAnEgressMapperOwnedByItsIntegration() {
    var egressId = UUID.randomUUID();
    var contribution =
        new PluginMapperContribution(
            egressId,
            "COT Egress",
            "Encodes CoT",
            1,
            MapperQualifier.EGRESS,
            TEST_INTEGRATION.id());

    var plan =
        PluginMapperCatalogReconciliationPlanFactory.create(
            MapperQualifier.EGRESS, List.of(contribution), List.of());

    assertThat(plan.insertions())
        .singleElement()
        .satisfies(
            mapper -> {
              assertThat(mapper.id()).isEqualTo(egressId);
              assertThat(mapper.qualifier()).isEqualTo(MapperQualifier.EGRESS);
              assertThat(mapper.editable()).isFalse();
            });
    assertThat(plan.integrationAssignments())
        .containsExactly(new IntegrationAssignment(egressId, TEST_INTEGRATION.id()));
  }

  @Test
  void anEgressContributionCannotTakeTheIdentifierOfAnIngressMapper() {
    var sharedId = UUID.randomUUID();
    var contribution =
        new PluginMapperContribution(
            sharedId, "COT Egress", null, 1, MapperQualifier.EGRESS, TEST_INTEGRATION.id());

    var plan =
        PluginMapperCatalogReconciliationPlanFactory.create(
            MapperQualifier.EGRESS,
            List.of(contribution),
            List.of(pluginMapper(sharedId, "COT", null, 1, false)));

    assertThat(plan.insertions()).isEmpty();
    assertThat(plan.rejections())
        .extracting(Rejection::reason)
        .containsExactly(RejectionReason.QUALIFIER_UUID_OWNER);
  }

  @Test
  void handsAnEntryAnotherIntegrationOwnsToItsContributingIntegration() {
    var mapperId = UUID.randomUUID();
    var otherOwned =
        new MapperCatalogProfile(
            mapperId,
            1,
            "Contributed mapper",
            null,
            false,
            false,
            false,
            MapperQualifier.INGRESS,
            OTHER_INTEGRATION_ID);

    var plan =
        createPlan(new TestDecoder(mapperId, "Contributed mapper", null, 1), List.of(otherOwned));

    assertThat(plan.insertions()).isEmpty();
    assertThat(plan.integrationAssignments())
        .containsExactly(new IntegrationAssignment(mapperId, TEST_INTEGRATION.id()));
  }

  @Test
  void rejectsContributionsOfAnotherDirection() {
    var contribution =
        new PluginMapperContribution(
            UUID.randomUUID(),
            "COT Egress",
            null,
            1,
            MapperQualifier.EGRESS,
            TEST_INTEGRATION.id());

    assertThatThrownBy(
            () ->
                PluginMapperCatalogReconciliationPlanFactory.create(
                    MapperQualifier.INGRESS, List.of(contribution), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static PluginMapperCatalogReconciliationPlan createPlan(
      TestDecoder contribution, List<MapperCatalogProfile> persistedCatalog) {
    return createPlan(List.of(contribution), persistedCatalog);
  }

  private static PluginMapperCatalogReconciliationPlan createPlan(
      List<TestDecoder> contributions, List<MapperCatalogProfile> persistedCatalog) {
    return PluginMapperCatalogReconciliationPlanFactory.create(
        MapperQualifier.INGRESS,
        PluginMapperContributionFactory.ingress(
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(TEST_INTEGRATION, List.copyOf(contributions))))),
        persistedCatalog);
  }

  private static MapperCatalogProfile pluginMapper(
      UUID mapperId, String name, String description, int version, boolean archived) {
    return new MapperCatalogProfile(
        mapperId,
        version,
        name,
        description,
        false,
        false,
        archived,
        MapperQualifier.INGRESS,
        TEST_INTEGRATION.id());
  }

  private static MapperCatalogProfile catalogMapper(
      UUID mapperId, String name, boolean mappingDefinitionPresent, int version, boolean editable) {
    return new MapperCatalogProfile(
        mapperId,
        version,
        name,
        null,
        editable,
        mappingDefinitionPresent,
        false,
        MapperQualifier.INGRESS,
        null);
  }

  private record TestDecoder(UUID id, String name, String description, int version)
      implements PayloadIngressMapper<Feature> {

    private TestDecoder(UUID id) {
      this(id, "Contributed mapper", "Provider-declared description", 7);
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }
}
