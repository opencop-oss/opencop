// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.create;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What every connection creation asks for, whatever its transport.
 *
 * @param name the connection's name, validated by the creation
 * @param access the raw access mode the request named, validated by the creation
 * @param integration the connection schema version the connection binds to, or null when the
 *     request named none
 * @param extractorId the extractor that reads the input, or null when the request named none
 * @param input the values and files the request supplied for that extractor's declared fields
 * @param ingressMapperId the mapper the connection reads with, or null when the request named none
 * @param requestedBy the user creating the connection, who becomes its owner
 * @param settings the integration settings overrides as the request sent them, a parsed JSON object
 *     of plain values, or null when the request sent none
 */
public record ConnectionCreationDetails(
    String name,
    String access,
    IntegrationConnectionSchemaReference integration,
    UUID extractorId,
    ConnectionInput input,
    UUID ingressMapperId,
    Caller requestedBy,
    Map<String, ?> settings) {

  /** The request field naming the access mode, which a rejection points a client at. */
  public static final String ACCESS_FIELD = "access";

  /** The request field naming the integration, which a rejection points a client at. */
  public static final String INTEGRATION_FIELD = "integration";

  /** The request field naming the extractor, which a rejection points a client at. */
  public static final String EXTRACTOR_FIELD = "extractorId";

  /** The request field naming the ingress mapper, which a rejection points a client at. */
  public static final String INGRESS_MAPPER_FIELD = "mapperId";

  /** The request field carrying the integration settings overrides. */
  public static final String SETTINGS_FIELD = "settings";

  /** The request field naming the egress mapper, which a rejection points a client at. */
  public static final String EGRESS_MAPPER_FIELD = "egressMapperId";

  public ConnectionCreationDetails {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(requestedBy, "requestedBy");
  }
}
