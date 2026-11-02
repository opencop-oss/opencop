// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import java.util.UUID;

/**
 * Translation keys for text an integration sends (names, connection method and field labels).
 *
 * <p>Keys are built from ids that are already part of the contract, so rewording a label keeps its
 * key. Nothing is stored. Format: dot-separated paths under {@code integrations.<integrationId>},
 * with lowercase UUIDs.
 */
final class IntegrationTextKeys {

  private static final String ROOT = "integrations.";

  private IntegrationTextKeys() {}

  static String name(UUID integrationId) {
    return integration(integrationId) + ".name";
  }

  static String description(UUID integrationId) {
    return integration(integrationId) + ".description";
  }

  static String extractorLabel(UUID integrationId, UUID extractorId) {
    return extractor(integrationId, extractorId) + ".label";
  }

  /** {@code fieldName} is used as-is, so a name with {@code .} won't match a client translation. */
  static String fieldLabel(UUID integrationId, UUID extractorId, String fieldName) {
    return extractor(integrationId, extractorId) + ".fields." + fieldName;
  }

  private static String integration(UUID integrationId) {
    return ROOT + integrationId;
  }

  private static String extractor(UUID integrationId, UUID extractorId) {
    return integration(integrationId) + ".extractors." + extractorId;
  }
}
