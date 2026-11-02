// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability;
import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapabilityPolicy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Routes effective supported capabilities through provider-owned index resources while skipping
 * rejected resources. It returns registration class names to Spring so Spring remains responsible
 * for configuration parsing and bean registration.
 */
public final class IntegrationCapabilityImportSelector
    implements DeferredImportSelector, BeanClassLoaderAware {

  private ClassLoader beanClassLoader;

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    this.beanClassLoader = classLoader;
  }

  @Override
  public String[] selectImports(AnnotationMetadata importingClassMetadata) {
    return new String[0];
  }

  @Override
  public Class<? extends Group> getImportGroup() {
    return CapabilityGroup.class;
  }

  /** Aggregates declarations available in one deferred-selector round before validation. */
  public static final class CapabilityGroup implements Group {

    private final EnumSet<IntegrationCapability> declaredSupported =
        EnumSet.noneOf(IntegrationCapability.class);
    private final EnumSet<IntegrationCapability> rejected =
        EnumSet.noneOf(IntegrationCapability.class);
    private final Map<String, AnnotationMetadata> importingMetadata = new LinkedHashMap<>();

    private ClassLoader classLoader;

    @Override
    public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
      var attributes =
          metadata.getAnnotationAttributes(EnableIntegrationCapabilities.class.getName(), false);
      if (attributes == null) {
        throw new IllegalArgumentException(
            "Missing @EnableIntegrationCapabilities metadata on " + metadata.getClassName());
      }
      declaredSupported.addAll(
          Arrays.asList((IntegrationCapability[]) attributes.get("supported")));
      rejected.addAll(Arrays.asList((IntegrationCapability[]) attributes.get("rejected")));
      importingMetadata.putIfAbsent(metadata.getClassName(), metadata);
      classLoader = ((IntegrationCapabilityImportSelector) selector).beanClassLoader;
    }

    @Override
    public Iterable<Entry> selectImports() {
      var policy = resolvePolicy();
      var metadata = importingMetadata.values().iterator().next();
      var imports = new LinkedHashMap<String, AnnotationMetadata>();
      imports.put(IntegrationCapabilityInfrastructureConfiguration.class.getName(), metadata);

      var reader = new IntegrationCapabilityIndexReader(classLoader);
      for (var capability : policy.effectiveSupported()) {
        for (var registrationClass : reader.read(capability)) {
          imports.putIfAbsent(registrationClass, metadata);
        }
      }

      return imports.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(entry -> new Entry(entry.getValue(), entry.getKey()))
          .toList();
    }

    private IntegrationCapabilityPolicy resolvePolicy() {
      try {
        return IntegrationCapabilityPolicy.resolve(declaredSupported, rejected);
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException(
            "Invalid integration capability policy: "
                + exception.getMessage()
                + ". @EnableIntegrationCapabilities declarations must be available before "
                + "deferred-selector processing; place the complete host policy on initial "
                + "application configuration or ordinary imports. Declarations introduced by "
                + "another DeferredImportSelector or auto-configuration are unsupported.",
            exception);
      }
    }
  }
}
