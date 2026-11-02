// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationCapabilityIndexTest {

  @TempDir Path temporaryDirectory;

  @Test
  void exposesOnlyTheIngressIndexWithEveryExistingMapperProvider() throws Exception {
    var classLoader = IntegrationCapabilityIndexTest.class.getClassLoader();

    assertThat(new IntegrationCapabilityIndexReader(classLoader).read(INGRESS_MAPPING))
        .containsExactly(
            "com.opencop.core.sdk.integration.builtin.technical.config.NativeIngressMappingConfiguration");
    assertThat(
            Collections.list(
                classLoader.getResources(
                    "META-INF/opencop/integration-capabilities/v1/payload-mapping.imports")))
        .isEmpty();
  }

  @Test
  void exposesNoEgressMapperWithoutAnInstalledProvider() {
    var classLoader = IntegrationCapabilityIndexTest.class.getClassLoader();

    assertThat(new IntegrationCapabilityIndexReader(classLoader).read(EGRESS_MAPPING)).isEmpty();
  }

  @Test
  void mergesEveryResourceAsAnImmutableOrderIndependentSetWithoutLoadingClasses() throws Exception {
    var firstRoot = temporaryDirectory.resolve("first");
    var secondRoot = temporaryDirectory.resolve("second");
    writeIndex(
        firstRoot,
        """
        # provider metadata
          example.SecondConfiguration

        example.SharedConfiguration
        """);
    writeIndex(
        secondRoot,
        """
        example.SharedConfiguration
          example.FirstConfiguration
        # ignored full-line comment
        """);

    try (var forward = classLoader(firstRoot, secondRoot);
        var reversed = classLoader(secondRoot, firstRoot)) {
      var expected =
          List.of(
              "example.FirstConfiguration",
              "example.SecondConfiguration",
              "example.SharedConfiguration");

      var forwardResult = new IntegrationCapabilityIndexReader(forward).read(INGRESS_MAPPING);
      var reversedResult = new IntegrationCapabilityIndexReader(reversed).read(INGRESS_MAPPING);

      assertThat(forwardResult).containsExactlyElementsOf(expected).isUnmodifiable();
      assertThat(reversedResult).containsExactlyElementsOf(expected);
      assertThat(forward.requestedClasses()).noneMatch(name -> name.startsWith("example."));
      assertThat(reversed.requestedClasses()).noneMatch(name -> name.startsWith("example."));
    }
  }

  @Test
  void treatsMissingAndEmptyResourcesAsNoContribution() throws Exception {
    var missingRoot = temporaryDirectory.resolve("missing");
    Files.createDirectories(missingRoot);
    var emptyRoot = temporaryDirectory.resolve("empty");
    writeIndex(emptyRoot, "  \n# comment only\n\n");

    try (var missing = classLoader(missingRoot);
        var empty = classLoader(emptyRoot)) {
      assertThat(new IntegrationCapabilityIndexReader(missing).read(INGRESS_MAPPING)).isEmpty();
      assertThat(new IntegrationCapabilityIndexReader(empty).read(INGRESS_MAPPING)).isEmpty();
    }
  }

  @Test
  void reportsCapabilityAndLogicalPathWhenIndexEnumerationFails() {
    var failure = new IOException("index enumeration failed");
    var classLoader =
        new ClassLoader(null) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw failure;
          }
        };

    assertThatThrownBy(
            () -> new IntegrationCapabilityIndexReader(classLoader).read(INGRESS_MAPPING))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage(
            "Failed to read INGRESS_MAPPING capability index at "
                + "META-INF/opencop/integration-capabilities/v1/ingress-mapping.imports")
        .hasCause(failure);
  }

  @Test
  void reportsCapabilityAndLogicalPathWhenAnIndexStreamFails() throws MalformedURLException {
    var failure = new IOException("index stream failed");
    var resource = failingResource(failure);
    var classLoader =
        new ClassLoader(null) {
          @Override
          public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(List.of(resource));
          }
        };

    assertThatThrownBy(
            () -> new IntegrationCapabilityIndexReader(classLoader).read(INGRESS_MAPPING))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage(
            "Failed to read INGRESS_MAPPING capability index at "
                + "META-INF/opencop/integration-capabilities/v1/ingress-mapping.imports")
        .hasCause(failure);
  }

  private static TrackingClassLoader classLoader(Path... roots) throws IOException {
    var urls = new URL[roots.length];
    for (var index = 0; index < roots.length; index++) {
      urls[index] = roots[index].toUri().toURL();
    }
    return new TrackingClassLoader(urls);
  }

  private static void writeIndex(Path root, String content) throws IOException {
    var resource =
        root.resolve("META-INF/opencop/integration-capabilities/v1/ingress-mapping.imports");
    Files.createDirectories(resource.getParent());
    Files.writeString(resource, content, StandardCharsets.UTF_8);
  }

  private static URL failingResource(IOException failure) throws MalformedURLException {
    return URL.of(
        URI.create("test:index"),
        new URLStreamHandler() {
          @Override
          protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
              @Override
              public void connect() {}

              @Override
              public InputStream getInputStream() throws IOException {
                throw failure;
              }
            };
          }
        });
  }

  private static final class TrackingClassLoader extends URLClassLoader {

    private final List<String> requestedClasses = new ArrayList<>();

    private TrackingClassLoader(URL[] urls) {
      super(urls, null);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      requestedClasses.add(name);
      return super.loadClass(name, resolve);
    }

    private List<String> requestedClasses() {
      return List.copyOf(requestedClasses);
    }
  }
}
