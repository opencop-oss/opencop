// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability;
import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapabilityCatalog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Reads every UTF-8 {@code
 * META-INF/opencop/integration-capabilities/v1/<stable-capability-key>.imports} resource from the
 * startup classloader. Blank lines and full-line comments are ignored; remaining class names form
 * an unordered, deduplicated set returned in stable order. Discovery reads names only and must
 * never load or execute plugin code.
 */
final class IntegrationCapabilityIndexReader {

  private static final String RESOURCE_PREFIX = "META-INF/opencop/integration-capabilities/v1/";
  private static final IntegrationCapabilityCatalog CAPABILITY_CATALOG =
      IntegrationCapabilityCatalog.standard();

  private final ClassLoader classLoader;

  IntegrationCapabilityIndexReader(ClassLoader classLoader) {
    this.classLoader = Objects.requireNonNull(classLoader);
  }

  List<String> read(IntegrationCapability capability) {
    var registrations = new TreeSet<String>();
    var resourcePath =
        RESOURCE_PREFIX + CAPABILITY_CATALOG.contractFor(capability).stableKey() + ".imports";
    try {
      var resources = classLoader.getResources(resourcePath);
      while (resources.hasMoreElements()) {
        var resource = resources.nextElement();
        try (var reader =
            new BufferedReader(
                new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
          for (String line; (line = reader.readLine()) != null; ) {
            var registrationClass = line.trim();
            if (!registrationClass.isEmpty() && !registrationClass.startsWith("#")) {
              registrations.add(registrationClass);
            }
          }
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to read " + capability + " capability index at " + resourcePath, exception);
    }
    return List.copyOf(registrations);
  }
}
