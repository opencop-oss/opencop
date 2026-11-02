// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class IngressMapperTraitWatchboxMapperIdentityTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  @Test
  void returnsTrueForStableDecoderId() {
    var mapperId = UUID.randomUUID();
    var identity =
        new IngressMapperTraitWatchboxMapperIdentity(
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION,
                        List.of(new FakeDecoder(mapperId, ObjectIdStability.STABLE_PER_ENTITY))))));

    assertThat(identity.hasStableObjectId(mapperId)).isTrue();
  }

  @Test
  void returnsFalseForUnknownDecoderId() {
    var identity =
        new IngressMapperTraitWatchboxMapperIdentity(
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION,
                        List.of(
                            new FakeDecoder(
                                UUID.randomUUID(), ObjectIdStability.STABLE_PER_ENTITY))))));

    assertThat(identity.hasStableObjectId(UUID.randomUUID())).isFalse();
    assertThat(identity.hasStableObjectId(null)).isFalse();
  }

  @Test
  void returnsFalseForDecoderWithDefaultUnknownStability() {
    var mapperId = UUID.randomUUID();
    var identity =
        new IngressMapperTraitWatchboxMapperIdentity(
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION, List.of(new DefaultUnknownDecoder(mapperId))))));

    assertThat(identity.hasStableObjectId(mapperId)).isFalse();
  }

  @Test
  void returnsFalseForDuplicateDecoderIds() {
    var mapperId = UUID.randomUUID();
    var identity =
        new IngressMapperTraitWatchboxMapperIdentity(
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION,
                        List.of(
                            new FakeDecoder(mapperId, ObjectIdStability.STABLE_PER_ENTITY),
                            new FakeDecoder(mapperId, ObjectIdStability.STABLE_PER_ENTITY))))));

    assertThat(identity.hasStableObjectId(mapperId)).isFalse();
  }

  private static class FakeDecoder implements PayloadIngressMapper<Feature> {

    private final UUID id;
    private final ObjectIdStability stability;

    FakeDecoder(UUID id, ObjectIdStability stability) {
      this.id = id;
      this.stability = stability;
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public String name() {
      return "fake";
    }

    @Override
    public String description() {
      return "fake";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public ObjectIdStability objectIdStability() {
      return stability;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }

  private static class DefaultUnknownDecoder implements PayloadIngressMapper<Feature> {

    private final UUID id;

    DefaultUnknownDecoder(UUID id) {
      this.id = id;
    }

    @Override
    public UUID id() {
      return id;
    }

    @Override
    public String name() {
      return "default";
    }

    @Override
    public String description() {
      return "default";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }
}
