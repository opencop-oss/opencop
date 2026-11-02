// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.profile;

import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.BLANK_NAME_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.DUPLICATE_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.FIRST_DUPLICATE_NAME_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.GEOJSON_MAPPER_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.KLV_MAPPER_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.MAPPER_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.NEGATIVE_VERSION_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.SECOND_DUPLICATE_NAME_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.TAK_MAPPER_ID;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.TEST_INTEGRATION;
import static com.opencop.core.api.component.general.profile.PluginMapperRegistrationTestApplication.ZERO_VERSION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.usecase.profile.mapping.PluginMapperCatalogReconciliationUsecase;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = PluginMapperRegistrationTestApplication.class)
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PluginMapperCatalogReconciliationIT extends AbstractDatabaseIT {

  private static final UUID NAME_OWNER_ID = UUID.fromString("0866100c-10c5-470a-a352-14ae3882b21f");
  private static final UUID MISSING_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440701");
  private static final UUID DATA_AUTHORED_MAPPER_ID =
      UUID.fromString("d21bc2ee-0f76-4b8f-a1ad-a13f70e30b81");

  @Autowired private PluginMapperCatalogReconciliationUsecase usecase;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StartupLeader startupLeader;
  @MockitoSpyBean private IngressMapperContributions contributions;
  @MockitoSpyBean private MapperCatalogGateway catalog;

  @Autowired
  @Qualifier("pluginMapperRegistrar")
  private ApplicationRunner pluginMapperRegistrar;

  @AfterEach
  void resetProfiles() {
    jdbc.update("DELETE FROM profiles.data_mapper");
    jdbc.update("DELETE FROM profiles.mapper");
  }

  @Test
  void startupRunnerRegistersContribution() {
    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .hasSize(1);
    assertThat(owningIntegration(MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
  }

  @Test
  void nonLeaderStartupPerformsNoMapperWrites() throws Exception {
    jdbc.update("DELETE FROM profiles.data_mapper");
    jdbc.update("DELETE FROM profiles.mapper");
    when(startupLeader.isLeader()).thenReturn(false);

    pluginMapperRegistrar.run(null);

    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .isEmpty();
  }

  @Test
  void contributionIsInsertedOnceWithDeclaredMetadataAndOwnership() {
    usecase.reconcile();
    usecase.reconcile();

    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("name")).isEqualTo("Selectable plugin mapper");
              assertThat(row.get("description")).isEqualTo("Provider-declared description");
              assertThat(row.get("version")).isEqualTo(7);
              assertThat(row.get("editable")).isEqualTo(false);
              assertThat(row.get("archived")).isEqualTo(false);
              assertThat(row.get("created_at")).isNotNull();
            });
    assertThat(owningIntegration(MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
  }

  @Test
  void reconciliationReadsMapperDefinitionsContainingDerivedAttributeEntryFlag() {
    insertMapperIdentity(DATA_AUTHORED_MAPPER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 1, 'Existing data-authored mapper', 'Persisted by an earlier release', true,
                CAST(? AS jsonb), false)
        """,
        DATA_AUTHORED_MAPPER_ID,
        """
        {
          "id": "d21bc2ee-0f76-4b8f-a1ad-a13f70e30b81",
          "enabled": true,
          "rules": [
            {
              "id": "841d36ef-98ae-4b2c-bc72-bb3b044b4336",
              "enabled": true,
              "target": "DISPLAY_NAME",
              "source": {"type": "xpath", "expr": "name/text()"},
              "transforms": [],
              "onMissing": "ERROR",
              "attributeEntry": false
            }
          ]
        }
        """);

    assertThatCode(usecase::reconcile).doesNotThrowAnyException();
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?",
                UUID.class,
                DATA_AUTHORED_MAPPER_ID))
        .containsExactly(DATA_AUTHORED_MAPPER_ID);
  }

  @Test
  void unreadableHistoricalDefinitionDoesNotBlockCatalogReconciliation() throws Exception {
    jdbc.update(
        "DELETE FROM profiles.data_mapper WHERE id IN (?, ?, ?)",
        MAPPER_ID,
        GEOJSON_MAPPER_ID,
        TAK_MAPPER_ID);
    jdbc.update(
        "DELETE FROM profiles.mapper WHERE id IN (?, ?, ?)",
        MAPPER_ID,
        GEOJSON_MAPPER_ID,
        TAK_MAPPER_ID);
    insertMapperIdentity(MAPPER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 6, 'Selectable plugin mapper', 'Retained historical definition', false,
                CAST(? AS jsonb), false)
        """,
        MAPPER_ID,
        """
        {
          "id": "f726456c-19d2-4887-a8a2-36b48a2b88ca",
          "enabled": true,
          "rules": [],
          "removedHistoricalProperty": "must remain untouched"
        }
        """);
    var retainedMapperBefore =
        jdbc.queryForMap(
            """
            SELECT id, version, name, description, editable, mapper::text, archived
              FROM profiles.data_mapper
             WHERE id = ?
            """,
            MAPPER_ID);

    pluginMapperRegistrar.run(null);

    assertThat(
            jdbc.queryForMap(
                """
                SELECT id, version, name, description, editable, mapper::text, archived
                  FROM profiles.data_mapper
                 WHERE id = ?
                """,
                MAPPER_ID))
        .isEqualTo(retainedMapperBefore);
    assertThat(owningIntegration(MAPPER_ID)).isNull();
    assertThat(owningIntegration(GEOJSON_MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
    assertThat(owningIntegration(TAK_MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
  }

  @Test
  void integrationOwnedMapperReceivesProviderMetadataWithoutChangingItsPersistedVersion() {
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    insertMapperIdentity(MAPPER_ID, TEST_INTEGRATION.id());
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 11, 'Old name', 'Old description', false, CAST('null' AS jsonb), false)
        """,
        MAPPER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("name")).isEqualTo("Selectable plugin mapper");
              assertThat(row.get("description")).isEqualTo("Provider-declared description");
              assertThat(row.get("version")).isEqualTo(11);
            });
    assertThat(owningIntegration(MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void persistedMapperNameRemainsReservedByItsActiveOrArchivedOwner(boolean archived) {
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", GEOJSON_MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", GEOJSON_MAPPER_ID);
    insertMapperIdentity(NAME_OWNER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 6, 'Selectable plugin mapper', 'Catalog owner', true, CAST('null' AS jsonb), ?)
        """,
        NAME_OWNER_ID,
        archived);
    var ownerBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", NAME_OWNER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", NAME_OWNER_ID))
        .isEqualTo(ownerBefore);
    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .isEmpty();
    assertThat(owningIntegration(GEOJSON_MAPPER_ID)).isEqualTo(TEST_INTEGRATION.id());
  }

  @Test
  void persistedNameOnAnOlderMapperVersionRemainsReservedByItsOwner() {
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    insertMapperIdentity(NAME_OWNER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES
          (?, 1, 'Selectable plugin mapper', 'Historical owner name', true, CAST('null' AS jsonb),
           false),
          (?, 2, 'Current owner name', 'Latest owner name', true, CAST('null' AS jsonb), false)
        """,
        NAME_OWNER_ID,
        NAME_OWNER_ID);
    var ownerBefore =
        jdbc.queryForList(
            "SELECT * FROM profiles.data_mapper WHERE id = ? ORDER BY version", NAME_OWNER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForList("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM profiles.data_mapper WHERE id = ? ORDER BY version", NAME_OWNER_ID))
        .isEqualTo(ownerBefore);
  }

  @Test
  void aContributionCannotTakeOverAMapperNoIntegrationOwns() {
    replaceWithUnownedMapper(TAK_MAPPER_ID, false, "null");
    var rowBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID))
        .isEqualTo(rowBefore);
    assertThat(owningIntegration(TAK_MAPPER_ID)).isNull();
  }

  @Test
  void editableMapperIsNotTakenOverOrOverwritten() {
    replaceWithUnownedMapper(TAK_MAPPER_ID, true, "null");
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    var rowBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID))
        .isEqualTo(rowBefore);
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?", UUID.class, MAPPER_ID))
        .containsExactly(MAPPER_ID);
  }

  @Test
  void dataAuthoredMapperIsNotTakenOverOrOverwritten() {
    replaceWithUnownedMapper(
        TAK_MAPPER_ID,
        false,
        """
        {
          "id": "24e7279b-abff-4b68-a0c3-2225e6262ed6",
          "enabled": true,
          "rules": []
        }
        """);
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    var rowBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID);

    usecase.reconcile();

    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", TAK_MAPPER_ID))
        .isEqualTo(rowBefore);
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?", UUID.class, MAPPER_ID))
        .containsExactly(MAPPER_ID);
  }

  @Test
  void registrationReceivesOnlyAdmittedContributions() {
    assertThat(contributions.mappers())
        .extracting(decoder -> decoder.id())
        .containsExactlyInAnyOrder(
            MAPPER_ID,
            TAK_MAPPER_ID,
            GEOJSON_MAPPER_ID,
            KLV_MAPPER_ID,
            FIRST_DUPLICATE_NAME_ID,
            SECOND_DUPLICATE_NAME_ID);
  }

  @Test
  void invalidMetadataContributionsCreateNoCatalogRows() {
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id IN (?, ?, ?)",
                UUID.class,
                BLANK_NAME_ID,
                ZERO_VERSION_ID,
                NEGATIVE_VERSION_ID))
        .isEmpty();
  }

  @Test
  void duplicateUuidContributionsCreateNoCatalogRow() {
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?", UUID.class, DUPLICATE_ID))
        .isEmpty();
  }

  @Test
  void duplicateUnownedNamesAreAllRejectedWithoutBlockingUnrelatedContributions() {
    usecase.reconcile();

    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id IN (?, ?)",
                UUID.class,
                FIRST_DUPLICATE_NAME_ID,
                SECOND_DUPLICATE_NAME_ID))
        .isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?", UUID.class, MAPPER_ID))
        .containsExactly(MAPPER_ID);
  }

  @ParameterizedTest
  @MethodSource("admissionRejectedMapperIds")
  void admissionRejectedContributionArchivesItsExistingPluginMapper(UUID mapperId) {
    insertPluginMapper(mapperId, "Previously accepted " + mapperId, false);

    usecase.reconcile();

    assertThat(mapperArchivedStates(mapperId)).containsExactly(true);
  }

  @Test
  void duplicateNewNameRejectionArchivesEveryExistingPluginClaimant() {
    insertPluginMapper(FIRST_DUPLICATE_NAME_ID, "Old first name", false);
    insertPluginMapper(SECOND_DUPLICATE_NAME_ID, "Old second name", false);

    usecase.reconcile();

    assertThat(mapperArchivedStates(FIRST_DUPLICATE_NAME_ID)).containsExactly(true);
    assertThat(mapperArchivedStates(SECOND_DUPLICATE_NAME_ID)).containsExactly(true);
  }

  @Test
  void persistedOwnerCollisionArchivesTheRejectedExistingPluginMapper() {
    jdbc.update(
        "UPDATE profiles.data_mapper SET name = 'Old provider name' WHERE id = ?", MAPPER_ID);
    insertMapperIdentity(NAME_OWNER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, editable, mapper, archived)
        VALUES (?, 1, 'Selectable plugin mapper', true, CAST('null' AS jsonb), false)
        """,
        NAME_OWNER_ID);

    usecase.reconcile();

    assertThat(mapperArchivedStates(MAPPER_ID)).containsExactly(true);
    assertThat(mapperArchivedStates(NAME_OWNER_ID)).containsExactly(false);
  }

  @Test
  void missingContributionArchivesAndReturningContributionReenablesTheSameMapperHistory() {
    insertPluginMapper(MISSING_MAPPER_ID, "Returning mapper", false);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, editable, mapper, archived)
        VALUES (?, 4, 'Returning mapper', false, CAST('null' AS jsonb), false)
        """,
        MISSING_MAPPER_ID);
    var historyBefore =
        jdbc.queryForList(
            """
            SELECT id, version, name, description, editable, mapper::text,
                   created_at, created_by, updated_at, updated_by
              FROM profiles.data_mapper
             WHERE id = ?
             ORDER BY version
            """,
            MISSING_MAPPER_ID);

    usecase.reconcile();

    assertThat(mapperArchivedStates(MISSING_MAPPER_ID)).containsExactly(true, true);
    assertThat(mapperProfileService.getAllMapperProfiles(Set.of(MapperQualifier.INGRESS)))
        .noneMatch(mapper -> mapper.id().equals(MISSING_MAPPER_ID));
    assertThat(mapperProfileService.getAllMapperProfilesAdmin(Set.of(MapperQualifier.INGRESS)))
        .filteredOn(mapper -> mapper.id().equals(MISSING_MAPPER_ID))
        .singleElement()
        .satisfies(
            mapper -> {
              assertThat(mapper.archived()).isTrue();
              assertThat(mapper.integrationId()).isEqualTo(TEST_INTEGRATION.id());
              assertThat(mapper.version()).isEqualTo(4);
            });

    when(contributions.groups())
        .thenReturn(List.of(testGroup(new TestDecoder(MISSING_MAPPER_ID, "Returning mapper", 9))));
    usecase.reconcile();

    assertThat(mapperArchivedStates(MISSING_MAPPER_ID)).containsExactly(false, false);
    assertThat(mapperHistoryWithoutAvailability(MISSING_MAPPER_ID)).isEqualTo(historyBefore);
  }

  @Test
  void nonLeaderStartupPerformsNoLifecycleWrites() throws Exception {
    insertPluginMapper(MISSING_MAPPER_ID, "Missing mapper", false);
    when(startupLeader.isLeader()).thenReturn(false);

    pluginMapperRegistrar.run(null);

    assertThat(mapperArchivedStates(MISSING_MAPPER_ID)).containsExactly(false);
  }

  @Test
  void lifecycleWriteFailureRollsBackTheCompleteReconciliationPlan() {
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", GEOJSON_MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", GEOJSON_MAPPER_ID);
    replaceWithUnownedMapper(KLV_MAPPER_ID, false, "null");
    jdbc.update(
        "UPDATE profiles.data_mapper SET name = 'Old name', description = 'Old description', archived = true WHERE id = ?",
        MAPPER_ID);
    insertPluginMapper(BLANK_NAME_ID, "Previously valid mapper", false);
    var mapperBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    var unownedBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", KLV_MAPPER_ID);
    var missingBefore =
        jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", BLANK_NAME_ID);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("induced mapper lifecycle failure");
            })
        .when(catalog)
        .reconcileAvailability(eq(BLANK_NAME_ID), eq(true));

    assertThatThrownBy(usecase::reconcile)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("induced mapper lifecycle failure");

    assertThat(
            jdbc.queryForList(
                "SELECT id FROM profiles.data_mapper WHERE id = ?", UUID.class, GEOJSON_MAPPER_ID))
        .isEmpty();
    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", MAPPER_ID))
        .isEqualTo(mapperBefore);
    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", KLV_MAPPER_ID))
        .isEqualTo(unownedBefore);
    assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_mapper WHERE id = ?", BLANK_NAME_ID))
        .isEqualTo(missingBefore);
  }

  @Test
  void contributedMapperAppearsInActiveAndAdministrativeListings() {
    usecase.reconcile();

    assertThat(mapperProfileService.getAllMapperProfiles(Set.of(MapperQualifier.INGRESS)))
        .filteredOn(profile -> profile.id().equals(MAPPER_ID))
        .singleElement()
        .satisfies(profile -> assertThat(profile.integrationId()).isEqualTo(TEST_INTEGRATION.id()));
    assertThat(mapperProfileService.getAllMapperProfilesAdmin(Set.of(MapperQualifier.INGRESS)))
        .filteredOn(profile -> profile.id().equals(MAPPER_ID))
        .singleElement()
        .satisfies(profile -> assertThat(profile.integrationId()).isEqualTo(TEST_INTEGRATION.id()));
  }

  private static List<UUID> admissionRejectedMapperIds() {
    return List.of(BLANK_NAME_ID, ZERO_VERSION_ID, NEGATIVE_VERSION_ID, DUPLICATE_ID);
  }

  private List<?> mapperHistoryWithoutAvailability(UUID mapperId) {
    return jdbc.queryForList(
        """
        SELECT id, version, name, description, editable, mapper::text,
               created_at, created_by, updated_at, updated_by
          FROM profiles.data_mapper
         WHERE id = ?
         ORDER BY version
        """,
        mapperId);
  }

  private UUID owningIntegration(UUID mapperId) {
    return jdbc.queryForObject(
        "SELECT integration_id FROM profiles.mapper WHERE id = ?", UUID.class, mapperId);
  }

  private List<Boolean> mapperArchivedStates(UUID mapperId) {
    return jdbc.queryForList(
        "SELECT archived FROM profiles.data_mapper WHERE id = ? ORDER BY version",
        Boolean.class,
        mapperId);
  }

  private void insertMapperIdentity(UUID mapperId) {
    insertMapperIdentity(mapperId, null);
  }

  private void insertMapperIdentity(UUID mapperId, UUID integrationId) {
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier, integration_id)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId,
        integrationId);
  }

  private void insertPluginMapper(UUID mapperId, String name, boolean archived) {
    insertMapperIdentity(mapperId, TEST_INTEGRATION.id());
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, editable, mapper, archived)
        VALUES (?, 1, ?, false, CAST('null' AS jsonb), ?)
        """,
        mapperId,
        name,
        archived);
  }

  /** Replaces a contributed mapper with one no integration owns, under the same identifier. */
  private void replaceWithUnownedMapper(UUID mapperId, boolean editable, String mapperDefinition) {
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", mapperId);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", mapperId);
    insertMapperIdentity(mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper
          (id, version, name, description, editable, mapper, archived)
        VALUES (?, 5, ?, 'Catalog description', ?, CAST(? AS jsonb), false)
        """,
        mapperId,
        "Catalog " + mapperId,
        editable,
        mapperDefinition);
  }

  private record TestDecoder(UUID id, String name, int version)
      implements PayloadIngressMapper<Feature> {

    @Override
    public String description() {
      return null;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }

  private static IntegrationIngressMappers testGroup(PayloadIngressMapper<Feature> decoder) {
    return new IntegrationIngressMappers(
        PluginMapperRegistrationTestApplication.TEST_INTEGRATION, List.of(decoder));
  }
}
