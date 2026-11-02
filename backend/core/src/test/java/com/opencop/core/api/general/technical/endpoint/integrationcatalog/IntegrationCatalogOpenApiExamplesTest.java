// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import com.opencop.core.api.general.business.policy.IntegrationCatalogPolicy;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Annotations compile whatever they claim, so these pin the published examples to the contract they
 * document: the response record and the rejection this endpoint actually produces.
 */
class IntegrationCatalogOpenApiExamplesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void everyPublishedExampleIsValidJson() throws Exception {
    for (var field : IntegrationCatalogOpenApiExamples.class.getDeclaredFields()) {
      if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
        field.setAccessible(true);
        assertThat(objectMapper.readValue((String) field.get(null), Object.class))
            .as(field.getName())
            .isNotNull();
      }
    }
  }

  @Test
  void theCatalogExampleDeserializesIntoTheResponseItDocuments() {
    var entries = catalogExample();

    assertThat(entries).hasSize(2);
    assertThat(entries)
        .allSatisfy(
            entry -> {
              assertThat(entry.integrationId()).isNotNull();
              assertThat(entry.schemaVersion()).isPositive();
              assertThat(entry.name()).isNotBlank();
              assertThat(entry.description()).isNotBlank();
              assertThat(entry.nameKey())
                  .isEqualTo(IntegrationTextKeys.name(entry.integrationId()));
              assertThat(entry.descriptionKey())
                  .isEqualTo(IntegrationTextKeys.description(entry.integrationId()));
            });
  }

  /**
   * The example is a client's reference for the two availability states and for the repetition the
   * flat shape causes, so it shows two versions of one integration: both have to appear, since
   * showing only the available one would hide the field's whole purpose, and sharing the identifier
   * is what tells a client the rows are not keyed on it.
   */
  @Test
  void theCatalogExampleShowsBothAvailabilityStatesAcrossTwoVersionsOfOneIntegration() {
    var entries = catalogExample();

    assertThat(entries)
        .extracting(IntegrationCatalogEntryResponse::available)
        .containsExactlyInAnyOrder(true, false);
    assertThat(entries)
        .extracting(IntegrationCatalogEntryResponse::integrationId)
        .hasSize(2)
        .containsOnly(entries.getFirst().integrationId());
    assertThat(entries)
        .extracting(IntegrationCatalogEntryResponse::schemaVersion)
        .doesNotHaveDuplicates();
    assertThat(entries)
        .extracting(
            IntegrationCatalogEntryResponse::name, IntegrationCatalogEntryResponse::description)
        .containsOnly(tuple(entries.getFirst().name(), entries.getFirst().description()));
  }

  @Test
  void theCatalogExampleNamesNoFieldTheContractDoesNotDefine() {
    assertThat(propertyNames(IntegrationCatalogOpenApiExamples.CATALOG_RESPONSE))
        .containsExactlyInAnyOrder(
            "integrationId",
            "schemaVersion",
            "name",
            "description",
            "nameKey",
            "descriptionKey",
            "available");
  }

  /**
   * Pins the documented rejection to the one the policy raises, so adding a transport protocol or
   * rewording the message cannot leave the published example claiming something else.
   */
  @Test
  void theUnknownProtocolExampleMatchesTheRejectionThePolicyRaises() {
    var raised =
        catchThrowableOfType(
            UnknownTransportProtocolException.class,
            () -> IntegrationCatalogPolicy.parseProtocol("SCTP"));

    assertThat(
            objectMapper.readValue(
                IntegrationCatalogOpenApiExamples.UNKNOWN_PROTOCOL_RESPONSE,
                GlobalErrorResponse.class))
        .isEqualTo(
            new GlobalErrorResponse(UnknownTransportProtocolException.CODE, raised.getMessage()));
  }

  @Test
  void theUnknownProtocolExampleNamesEveryTransportProtocolThisBuildDefines() {
    var message = IntegrationCatalogOpenApiExamples.UNKNOWN_PROTOCOL_RESPONSE;

    for (var protocol : TransportProtocol.values()) {
      assertThat(message).as(protocol.name()).contains(protocol.name());
    }
  }

  private List<IntegrationCatalogEntryResponse> catalogExample() {
    return objectMapper.readValue(
        IntegrationCatalogOpenApiExamples.CATALOG_RESPONSE,
        new TypeReference<List<IntegrationCatalogEntryResponse>>() {});
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
      default -> {
        // Scalars carry no property names.
      }
    }
  }
}
