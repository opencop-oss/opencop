// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapabilityPolicy;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;

class IntegrationCapabilityPolicyContextTest {

  @Test
  void composesAnnotationsIntoOnePolicyAndImportsSupportedResourcesWithQualifiedNames() {
    capabilityContext(
            IdentityDeclaration.class, IngressDeclaration.class, SubtypeDecoderConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(IntegrationCapabilityPolicy.class);
              var policy = context.getBean(IntegrationCapabilityPolicy.class);
              assertThat(policy.declaredSupported())
                  .containsExactlyInAnyOrder(INTEGRATION_IDENTITY, INGRESS_MAPPING);
              assertThat(policy.effectiveSupported())
                  .containsExactlyInAnyOrder(INTEGRATION_IDENTITY, INGRESS_MAPPING);
              assertThat(policy.rejected())
                  .containsExactlyInAnyOrder(
                      EGRESS_MAPPING,
                      CONNECTION_SCHEMA,
                      CONNECTION_SETTINGS,
                      CONNECTION_BEHAVIOR,
                      CONNECTION_INPUT,
                      CONNECTION_MAPPING,
                      GEOCODING,
                      CONNECTION_EXTENSION);
              assertThat(context.getBeansOfType(Integration.class)).hasSize(2);
              assertThat(context.getBeansOfType(IngressMappingContribution.class)).hasSize(1);
              assertThat(context.getBean(DecoderCollectionProbe.class).decoders())
                  .anyMatch(SubtypeDecoder.class::isInstance);
              assertThat(context)
                  .hasBean(
                      "com.opencop.core.sdk.integration.builtin.technical.config."
                          + "NativeIntegrationIdentityConfiguration.nativeIntegration");
            });

    capabilityContext(
            IngressDeclaration.class, IdentityDeclaration.class, SubtypeDecoderConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(IntegrationCapabilityPolicy.class).effectiveSupported())
                  .containsExactlyInAnyOrder(INTEGRATION_IDENTITY, INGRESS_MAPPING);
            });
  }

  @Test
  void validatesEveryInvalidAggregatePolicyBeforeReadingAnIndex() {
    assertInvalidBeforeIndexRead(OverlappingPolicy.class);
    assertInvalidBeforeIndexRead(OmittedCapabilityPolicy.class);
    assertInvalidBeforeIndexRead(RejectedPrerequisitePolicy.class);
  }

  @Test
  void rejectsPolicyDeclarationsDiscoveredInALaterDeferredSelectorRoundWithGuidance() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.setBeanNameGenerator(FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
      context.register(InitialRoundPolicy.class);

      assertThatThrownBy(context::refresh)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "@EnableIntegrationCapabilities declarations must be available before deferred-selector processing");
    }
  }

  private static void assertInvalidBeforeIndexRead(Class<?> configuration) {
    var classLoader = new CountingClassLoader();
    var selector = new IntegrationCapabilityImportSelector();
    selector.setBeanClassLoader(classLoader);
    var group = new IntegrationCapabilityImportSelector.CapabilityGroup();
    group.process(AnnotationMetadata.introspect(configuration), selector);

    assertThatIllegalArgumentException().isThrownBy(group::selectImports);
    assertThat(classLoader.resourceReads).hasValue(0);
  }

  private static ApplicationContextRunner capabilityContext(Class<?>... configurations) {
    return new ApplicationContextRunner(
            () -> {
              var context = new AnnotationConfigApplicationContext();
              context.setBeanNameGenerator(
                  FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
              return context;
            })
        .withUserConfiguration(configurations);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INTEGRATION_IDENTITY,
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_SCHEMA,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_MAPPING,
        GEOCODING,
        CONNECTION_EXTENSION
      })
  static class IdentityDeclaration {}

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = {INGRESS_MAPPING, INGRESS_MAPPING},
      rejected = {EGRESS_MAPPING, CONNECTION_INPUT, CONNECTION_MAPPING})
  static class IngressDeclaration {}

  @Configuration(proxyBeanMethods = false)
  static class SubtypeDecoderConfiguration {

    @Bean
    SubtypeDecoder subtypeDecoder() {
      return new SubtypeDecoder();
    }

    @Bean
    DecoderCollectionProbe decoderCollectionProbe(
        List<PayloadIngressMapper<? extends Feature>> decoders) {
      return new DecoderCollectionProbe(List.copyOf(decoders));
    }
  }

  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        INGRESS_MAPPING,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_INPUT
      })
  static class OverlappingPolicy {}

  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {CONNECTION_MAPPING, INTEGRATION_IDENTITY, CONNECTION_SCHEMA, CONNECTION_BEHAVIOR})
  static class OmittedCapabilityPolicy {}

  @EnableIntegrationCapabilities(
      supported = CONNECTION_BEHAVIOR,
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        INGRESS_MAPPING,
        CONNECTION_INPUT
      })
  static class RejectedPrerequisitePolicy {}

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INTEGRATION_IDENTITY,
      rejected = {EGRESS_MAPPING, CONNECTION_BEHAVIOR, CONNECTION_MAPPING})
  @Import(ComplementaryPolicyDeferredSelector.class)
  static class InitialRoundPolicy {}

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {EGRESS_MAPPING, CONNECTION_INPUT, CONNECTION_MAPPING})
  static class LaterRoundPolicy {}

  static final class ComplementaryPolicyDeferredSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
      return new String[] {LaterRoundPolicy.class.getName()};
    }
  }

  private static final class CountingClassLoader extends ClassLoader {

    private final AtomicInteger resourceReads = new AtomicInteger();

    private CountingClassLoader() {
      super(IntegrationCapabilityPolicyContextTest.class.getClassLoader());
    }

    @Override
    public Enumeration<URL> getResources(String name) {
      resourceReads.incrementAndGet();
      return Collections.emptyEnumeration();
    }
  }

  private record DecoderCollectionProbe(List<PayloadIngressMapper<? extends Feature>> decoders) {}

  private static final class FeatureSubtype extends Feature {}

  private static final class SubtypeDecoder implements PayloadIngressMapper<FeatureSubtype> {

    @Override
    public UUID id() {
      return UUID.fromString("1a363620-c5f1-4c25-ab5b-d1718b0ea06a");
    }

    @Override
    public String name() {
      return "subtype";
    }

    @Override
    public String description() {
      return "subtype Spring injection fixture";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<FeatureSubtype> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of(new FeatureSubtype());
    }
  }
}
