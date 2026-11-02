// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import io.swagger.v3.oas.models.media.JsonSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

/**
 * How the API document publishes types springdoc would otherwise describe by introspecting their
 * Java class.
 *
 * <p>A JSON tree holds any JSON value, but introspection publishes the node class's getters as if
 * they were its fields. Every JSON tree is published as any value instead. Jackson 2's tree is
 * mapped too: the SCIM library exposes it in its patch operations.
 */
@Configuration
public class OpenApiDocumentConfiguration {

  static {
    var anyJsonValue = new JsonSchema();
    SpringDocUtils.getConfig()
        .replaceWithSchema(JsonNode.class, anyJsonValue)
        .replaceWithSchema(com.fasterxml.jackson.databind.JsonNode.class, anyJsonValue);
  }
}
