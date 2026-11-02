// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.export.ExportArtifactRepository;
import com.opencop.core.api.general.technical.model.export.ExportResponse;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@ComponentWebIT(classes = ExportWebTestApplication.class)
@TestPropertySource(
    properties = "spring.flyway.locations=filesystem:../sdk/src/main/resources/db/migration")
class ExportEndpointsIT extends AbstractWebIT {

  private static final UUID EXPORT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID CREATOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448004");
  private static final UUID ROW_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448005");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448006");
  private static final UUID LAYER_ROW_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448007");
  private static final UUID EXPORT_MAPPER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448103");
  private static final String OBJECT_ID = "object-alpha";
  private static final String LAYER_OBJECT_ID = "object-layer";
  private static final String DOWNLOAD_PATH = "/v1/exports/" + EXPORT_ID + "/download";
  private static final String BLOB_PATH = "exports/test.csv";
  private static final String INTERNAL_BLOB_URL =
      "http://azurite:10000/devstoreaccount1/opencop-common/" + BLOB_PATH;
  private static final String SIGNED_URL = INTERNAL_BLOB_URL + "?sig=abc";

  @Autowired private ExportArtifactRepository artifactRepository;
  @Autowired private ResourceLifecycleGuard lifecycleGuard;
  @Autowired private UserSpecificManager userSpecificManager;
  @Autowired private LayerManager<Layer, LayerMember> layerManager;
  @Autowired private BlobClient blobClient;
  @Autowired private BlobServiceClient blobServiceClient;
  @Autowired private BlobContainerClient blobContainerClient;
  @Autowired private com.azure.storage.blob.BlobClient azureBlobClient;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanExports() {
    jdbcTemplate.execute("DELETE FROM geo.feature_latest_active_helper");
    jdbcTemplate.execute("DELETE FROM geo.features");
    jdbcTemplate.execute("DELETE FROM app.export_artifact");
    jdbcTemplate.execute("DELETE FROM iam.server_layer_access");
    jdbcTemplate.execute("DELETE FROM iam.layer");
    jdbcTemplate.execute("DELETE FROM profiles.data_source");
    jdbcTemplate.execute("DELETE FROM profiles.data_mapper");
    jdbcTemplate.execute("DELETE FROM profiles.mapper");
    insertExportTestMapper();
    reset(
        lifecycleGuard,
        userSpecificManager,
        layerManager,
        blobClient,
        blobServiceClient,
        blobContainerClient,
        azureBlobClient);
    when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(blobContainerClient);
    when(blobContainerClient.getBlobClient(BLOB_PATH)).thenReturn(azureBlobClient);
    when(azureBlobClient.generateSas(any(BlobServiceSasSignatureValues.class)))
        .thenReturn("sig=abc");
    when(azureBlobClient.getBlobUrl()).thenReturn(INTERNAL_BLOB_URL);
    when(layerManager.get(LAYER_ID))
        .thenReturn(new Layer.Builder().withId(LAYER_ID).withName("Operations Map").build());
  }

  @Test
  void create_whenAuthorizedLayerExportSucceeds_returnsBackendDownloadPathWithoutRawBlobUrl()
      throws Exception {
    insertLayerExportRows();
    when(userSpecificManager.getUserLayers(CREATOR_ID))
        .thenReturn(
            java.util.List.of(
                new LayerInfo(
                    LAYER_ID,
                    "export layer",
                    LayerRole.OWNER,
                    LayerAccessScope.PRIVATE,
                    null,
                    true)));

    var result =
        mockMvc
            .perform(
                post("/v1/exports")
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"format":"CSV","subjects":[{"subjectType":"LAYER","subjectId":"%s","includeTransitiveData":true}]}
                        """
                            .formatted(LAYER_ID)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

    var response = readResponse(result, ExportResponse.class);
    var body = result.getResponse().getContentAsString();
    assertEquals("/v1/exports/" + response.exportId() + "/download", response.downloadPath());
    assertTrue(response.filename().contains("-selection-"));
    assertTrue(
        response.filename().endsWith(response.exportId().toString().substring(0, 8) + ".csv"));
    assertFalse(body.contains("blobPath"));
    assertFalse(body.contains("https://"));
    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertFalse(csv.contains(LAYER_ID + ",LAYER,object-alpha"));
    assertTrue(csv.contains(SOURCE_ID + ",SOURCE,object-alpha,Alpha"));
  }

  @Test
  void create_whenLayerExportOmitsDataSourceFlag_exportsOnlyLayerOwnedRows() throws Exception {
    insertLayerExportRows();
    insertLayerOwnedExportRow();
    when(userSpecificManager.getUserLayers(CREATOR_ID))
        .thenReturn(
            java.util.List.of(
                new LayerInfo(
                    LAYER_ID,
                    "export layer",
                    LayerRole.OWNER,
                    LayerAccessScope.PRIVATE,
                    null,
                    true)));

    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[{"subjectType":"LAYER","subjectId":"%s"}]}
                    """
                        .formatted(LAYER_ID)))
        .andExpect(status().isCreated());

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(csv.contains(LAYER_ID + ",LAYER,object-layer,Layer owned"));
    assertFalse(csv.contains(SOURCE_ID + ",SOURCE,object-alpha,Alpha"));
  }

  @Test
  void create_whenSourceExportSetsIncludeTransitiveDataFalse_acceptsAndExports() throws Exception {
    insertDataSource(SOURCE_ID, null);
    when(userSpecificManager.getUserServers(CREATOR_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));
    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[{"subjectType":"SOURCE","subjectId":"%s","includeTransitiveData":false}]}
                    """
                        .formatted(SOURCE_ID)))
        .andExpect(status().isCreated());

    verify(blobClient).upload(any(), any());
  }

  @Test
  void create_whenAuthorizedSubjectCollection_exportsEverySelectedActiveRow() throws Exception {
    insertDataSource(SOURCE_ID, null);
    insertFeature(SOURCE_ID, OBJECT_ID, ROW_ID, "Alpha");
    jdbcTemplate.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", LAYER_ID, "export-layer");
    insertLayerOwnedExportRow();
    when(userSpecificManager.getUserServers(CREATOR_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));
    when(userSpecificManager.getUserLayers(CREATOR_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "export layer",
                    LayerRole.OWNER,
                    LayerAccessScope.PRIVATE,
                    null,
                    false)));

    var result =
        mockMvc
            .perform(
                post("/v1/exports")
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"format":"CSV","subjects":[
                          {"subjectType":"SOURCE","subjectId":"%s"},
                          {"subjectType":"LAYER","subjectId":"%s","includeTransitiveData":false}
                        ]}
                        """
                            .formatted(SOURCE_ID, LAYER_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    var response = readResponse(result, ExportResponse.class);
    assertTrue(response.filename().contains("-selection-"));
    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(csv.contains(SOURCE_ID + ",SOURCE,object-alpha,Alpha"));
    assertTrue(csv.contains(LAYER_ID + ",LAYER,object-layer,Layer owned"));
    assertEquals(
        "SELECTION",
        jdbcTemplate.queryForObject("SELECT subject_type FROM app.export_artifact", String.class));
    assertEquals(
        response.exportId(),
        jdbcTemplate.queryForObject("SELECT subject_id FROM app.export_artifact", UUID.class));
  }

  /**
   * Contract: when subjects overlap (canonical case: SOURCE S selected directly AND LAYER L with
   * includeTransitiveData=true where S is assigned to L), the CSV must contain each feature row
   * exactly once. The source-batch query returns S's row, the layer-batch query returns the same
   * row again via transitive resolution, and {@code SubjectsExportStrategy.finalizeRows} dedupes by
   * row id before mapping/upload. If this test starts seeing two copies of the row, R-03 has
   * regressed.
   */
  @Test
  void create_whenSourceAndLayerSubjectsOverlapTransitively_writesEachRowExactlyOnce()
      throws Exception {
    insertLayerExportRows();
    when(userSpecificManager.getUserServers(CREATOR_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));
    when(userSpecificManager.getUserLayers(CREATOR_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "export layer",
                    LayerRole.OWNER,
                    LayerAccessScope.PRIVATE,
                    null,
                    false)));

    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[
                      {"subjectType":"SOURCE","subjectId":"%s"},
                      {"subjectType":"LAYER","subjectId":"%s","includeTransitiveData":true}
                    ]}
                    """
                        .formatted(SOURCE_ID, LAYER_ID)))
        .andExpect(status().isCreated());

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    // S's feature row resolved via both the direct SOURCE subject and the transitive LAYER path;
    // the CSV must show it exactly once.
    var occurrences = csv.lines().filter(line -> line.contains(ROW_ID.toString())).toList();
    assertEquals(1, occurrences.size());
    assertTrue(occurrences.get(0).contains(SOURCE_ID + ",SOURCE,object-alpha,Alpha"));
  }

  @Test
  void create_whenAuthorizedElementIdsMatchRows_exportsExactlyThoseElements() throws Exception {
    insertDataSource(SOURCE_ID, null);
    insertFeature(SOURCE_ID, OBJECT_ID, ROW_ID, "Alpha");
    jdbcTemplate.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", LAYER_ID, "export-layer");
    insertLayerOwnedExportRow();
    var unrequestedRowId = UUID.fromString("550e8400-e29b-41d4-a716-446655448008");
    insertFeature(SOURCE_ID, "object-unrequested", unrequestedRowId, "Unrequested");
    when(userSpecificManager.getUserServers(CREATOR_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));
    when(userSpecificManager.getUserLayers(CREATOR_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "export layer",
                    LayerRole.OWNER,
                    LayerAccessScope.PRIVATE,
                    null,
                    false)));

    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","elementIds":["%s","%s"]}
                    """
                        .formatted(ROW_ID, LAYER_ROW_ID)))
        .andExpect(status().isCreated());

    var uploadCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(blobClient).upload(any(), uploadCaptor.capture());
    var csv = new String(uploadCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(csv.contains(ROW_ID + "," + SOURCE_ID + ",SOURCE,object-alpha,Alpha"));
    assertTrue(csv.contains(LAYER_ROW_ID + "," + LAYER_ID + ",LAYER,object-layer,Layer owned"));
    assertFalse(csv.contains(unrequestedRowId.toString()));
  }

  @Test
  void create_whenSubjectCollectionContainsUnauthorizedLayer_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[{"subjectType":"LAYER","subjectId":"%s"}]}
                    """
                        .formatted(LAYER_ID)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenElementRowSourceIsUnauthorized_returnsForbiddenWithoutUpload() throws Exception {
    insertDataSource(SOURCE_ID, null);
    insertFeature(SOURCE_ID, OBJECT_ID, ROW_ID, "Alpha");

    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","elementIds":["%s"]}
                    """
                        .formatted(ROW_ID)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenPayloadMixesSubjectsAndElementIds_returnsBadRequestWithoutUpload()
      throws Exception {
    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[{"subjectType":"SOURCE","subjectId":"%s"}],"elementIds":["%s"]}
                    """
                        .formatted(SOURCE_ID, ROW_ID)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenElementIdDoesNotMatchActiveRow_returnsNotFoundWithoutUpload() throws Exception {
    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","elementIds":["%s"]}
                    """
                        .formatted(ROW_ID)))
        .andExpect(status().isNotFound());

    verifyNoInteractions(blobClient);
  }

  @Test
  void create_whenSourceExportSetsIncludeTransitiveDataTrue_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/exports")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(CREATOR_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"format":"CSV","subjects":[{"subjectType":"SOURCE","subjectId":"%s","includeTransitiveData":true}]}
                    """
                        .formatted(SOURCE_ID)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(blobClient);
  }

  @Test
  void download_whenCreatorRequestsUnexpiredExport_redirectsToSignedUrlWithoutBlobBytes()
      throws Exception {
    artifactRepository.save(artifact(CREATOR_ID, OffsetDateTime.now().plusHours(1)));

    mockMvc
        .perform(get(DOWNLOAD_PATH).with(authentication(TestAuthentications.oidcUser(CREATOR_ID))))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, SIGNED_URL))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

    verify(blobContainerClient).getBlobClient(BLOB_PATH);
    verify(azureBlobClient).generateSas(any(BlobServiceSasSignatureValues.class));
    verify(blobClient, never()).download(any());
  }

  @Test
  void download_whenCallerDidNotCreateExport_returnsForbiddenWithoutBlobBytes() throws Exception {
    artifactRepository.save(artifact(CREATOR_ID, OffsetDateTime.now().plusHours(1)));

    mockMvc
        .perform(
            get(DOWNLOAD_PATH).with(authentication(TestAuthentications.oidcUser(OTHER_USER_ID))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(blobClient);
    verify(blobServiceClient, never()).getBlobContainerClient(any());
  }

  @Test
  void download_whenExportExpired_returnsGoneWithoutBlobBytes() throws Exception {
    artifactRepository.save(artifact(CREATOR_ID, OffsetDateTime.now().minusMinutes(1)));

    mockMvc
        .perform(get(DOWNLOAD_PATH).with(authentication(TestAuthentications.oidcUser(CREATOR_ID))))
        .andExpect(status().isGone());

    verifyNoInteractions(blobClient);
    verify(blobServiceClient, never()).getBlobContainerClient(any());
  }

  private static ExportArtifact artifact(UUID creatorId, OffsetDateTime expiresAt) {
    return new ExportArtifact(
        EXPORT_ID,
        "LAYER",
        LAYER_ID,
        creatorId,
        "CSV",
        "exports/test.csv",
        "layer-export.csv",
        OffsetDateTime.now().minusMinutes(5),
        expiresAt);
  }

  private void insertLayerExportRows() {
    jdbcTemplate.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", LAYER_ID, "export-layer");
    insertDataSource(SOURCE_ID, null);
    jdbcTemplate.update(
        """
        INSERT INTO iam.server_layer_access (server_id, layer_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        SOURCE_ID,
        LAYER_ID,
        "VIEWER");
    insertFeature(SOURCE_ID, OBJECT_ID, ROW_ID, "Alpha");
  }

  private void insertExportTestMapper() {
    jdbcTemplate.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        EXPORT_MAPPER_ID);
    jdbcTemplate.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        EXPORT_MAPPER_ID,
        1,
        "mapper-export-it",
        "{}");
  }

  private void insertDataSource(UUID sourceId, OffsetDateTime archivedAt) {
    NativeIntegrationFixture.seed(jdbcTemplate);
    jdbcTemplate.update(
        """
        INSERT INTO profiles.data_source (
            id, name, ingress_mapper_id, connection_spec, archived_at,
            integration_id, connection_schema_version
        ) VALUES (?, ?, ?, CAST('{}' AS jsonb), ?, ?, ?)
        """,
        new Object[] {
          sourceId,
          "source-" + sourceId,
          EXPORT_MAPPER_ID,
          archivedAt,
          NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
          NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()
        },
        new int[] {
          Types.OTHER,
          Types.VARCHAR,
          Types.OTHER,
          Types.TIMESTAMP_WITH_TIMEZONE,
          Types.OTHER,
          Types.INTEGER
        });
  }

  private void insertLayerOwnedExportRow() {
    insertFeature(LAYER_ID, LAYER_OBJECT_ID, LAYER_ROW_ID, "Layer owned");
  }

  private void insertFeature(UUID sourceId, String objectId, UUID rowId, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO geo.features (
          id, feature_kind, source, object_id, created_at, start_at, geom, payload
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        rowId,
        "event",
        sourceId,
        objectId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        "POINT(1 2)",
        """
        {"type":"Feature","properties":{"name":"%s","attributes":{}}}
        """
            .formatted(name));
    jdbcTemplate.update(
        """
        INSERT INTO geo.feature_latest_active_helper (
          source, object_id, event_id, playback_at, created_at, first_emission_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        sourceId,
        objectId,
        rowId,
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T11:00:00Z"),
        OffsetDateTime.parse("2026-06-08T10:00:00Z"));
  }
}
