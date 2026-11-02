// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.NumberField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution.Rejected;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution.Resolved;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsSection;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsCapability;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubOnConnectBehavior;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionSettingsRegistryTest {

  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final TestConnectionSchema WITHOUT_SETTINGS =
      new TestConnectionSchema(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1, "Plain", "No settings");

  @Test
  void resolvesTheCapabilityDefaultsAtRegistration() {
    var registry =
        new ConnectionSettingsRegistry(List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, 5)));

    assertThat(registry.defaults(SCHEMA)).isEqualTo(new Volume(5));
    assertThat(registry.descriptor(SCHEMA)).isEqualTo(new VolumeSettings(SCHEMA, 5).descriptor());
  }

  @Test
  void givesSchemasWithoutSettingsTheEmptyDescriptorAndFixedValue() {
    var registry =
        new ConnectionSettingsRegistry(
            List.of(SCHEMA, WITHOUT_SETTINGS), List.of(new VolumeSettings(SCHEMA, 5)));

    assertThat(registry.defaults(WITHOUT_SETTINGS)).isSameAs(NoConnectionSettings.INSTANCE);
    assertThat(registry.descriptor(WITHOUT_SETTINGS))
        .isSameAs(ConnectionSettingsDescriptor.empty());
    assertThat(registry.resolve(WITHOUT_SETTINGS, Map.of()))
        .isEqualTo(
            new Resolved(
                SettingsDocument.empty(), SettingsDocument.empty(), NoConnectionSettings.INSTANCE));
  }

  @Test
  void resolvesStoredOverridesLikeTheDocumentTheyHold() {
    var registry =
        new ConnectionSettingsRegistry(List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, 5)));
    var stored = Map.of("audio", Map.<String, Object>of("volume", 3));

    assertThat(registry.resolve(SCHEMA, Optional.of(new SettingsOverrides(stored))))
        .isEqualTo(registry.resolve(SCHEMA, stored));
    assertThat(registry.resolve(SCHEMA, Optional.empty()))
        .isEqualTo(registry.resolve(SCHEMA, Map.of()));
  }

  @Test
  void rejectsSettingsForASchemaThatDeclaresNone() {
    var registry = new ConnectionSettingsRegistry(List.of(WITHOUT_SETTINGS), List.of());

    assertThat(registry.resolve(WITHOUT_SETTINGS, Map.of("audio", Map.of())))
        .isInstanceOfSatisfying(
            Rejected.class,
            rejected ->
                assertThat(rejected.violations())
                    .extracting(SettingsViolation::path, SettingsViolation::code)
                    .containsExactly(tuple("", "SETTINGS_NOT_SUPPORTED")));
  }

  @Test
  void resolvesNoSettingsForASchemaVersionThisRuntimeDoesNotRegister() {
    var registry = new ConnectionSettingsRegistry(List.of(SCHEMA), List.of());
    var unregistered = new IntegrationConnectionSchemaReference(UUID.randomUUID(), 1);

    assertThat(registry.resolve(unregistered, Map.of())).isInstanceOf(Resolved.class);
    assertThat(registry.resolve(unregistered, Map.of("volume", Map.of("level", 3))))
        .isInstanceOfSatisfying(
            Rejected.class,
            rejected ->
                assertThat(rejected.violations())
                    .extracting(SettingsViolation::code)
                    .containsExactly("SETTINGS_NOT_SUPPORTED"));
  }

  @Test
  void rejectsUnknownSchemaLookups() {
    var registry = new ConnectionSettingsRegistry(List.of(SCHEMA), List.of());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.defaults(WITHOUT_SETTINGS))
        .withMessageContaining("Unknown connection schema")
        .withMessageContaining(WITHOUT_SETTINGS.integrationId().toString());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.resolve(WITHOUT_SETTINGS, Map.of()));
  }

  @Test
  void rejectsACapabilityOfAnUnregisteredSchema() {
    var orphan = new VolumeSettings(WITHOUT_SETTINGS, 5);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionSettingsRegistry(List.of(SCHEMA), List.of(orphan)))
        .withMessageContaining("Unregistered connection schema")
        .withMessageContaining(WITHOUT_SETTINGS.integrationId().toString());
  }

  @Test
  void rejectsASecondCapabilityForOneSchema() {
    var first = new VolumeSettings(SCHEMA, 5);
    var second = new VolumeSettings(SCHEMA, 6);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionSettingsRegistry(List.of(SCHEMA), List.of(first, second)))
        .withMessageContaining("Multiple connection settings capabilities")
        .withMessageContaining(SCHEMA.integrationId().toString());
  }

  @Test
  void rejectsADefaultOutsideItsDeclaredConstraint() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionSettingsRegistry(
                    List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, 11))))
        .withMessageContaining("audio.volume must be at most 10");
  }

  @Test
  void rejectsANonFiniteDefault() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionSettingsRegistry(
                    List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, Double.NaN, false))))
        .withMessageContaining("audio.volume must be a finite number");
  }

  @Test
  void acceptsAFractionalDefaultForAFractionalField() {
    var registry =
        new ConnectionSettingsRegistry(
            List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, 2.5, false)));

    assertThat(registry.defaults(SCHEMA)).isEqualTo(new Volume(2.5));
  }

  @Test
  void rejectsDuplicateSchemas() {
    var duplicate =
        new TestConnectionSchema(SCHEMA.integrationId(), SCHEMA.schemaVersion(), "Other", "Other");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionSettingsRegistry(List.of(SCHEMA, duplicate), List.of()))
        .withMessageContaining("Duplicate connection schema");
  }

  @Test
  void acceptsBehaviorsReadingTheirSchemaSettingsType() {
    var registry =
        new ConnectionSettingsRegistry(
            List.of(SCHEMA, WITHOUT_SETTINGS), List.of(new VolumeSettings(SCHEMA, 5)));

    registry.requireReadableBy(
        List.of(new StubOnConnectBehavior(WITHOUT_SETTINGS, "plain", (_, _) -> new byte[] {1})));
  }

  @Test
  void rejectsABehaviorReadingAnotherSettingsType() {
    var registry =
        new ConnectionSettingsRegistry(List.of(SCHEMA), List.of(new VolumeSettings(SCHEMA, 5)));
    var mismatched = new StubOnConnectBehavior(SCHEMA, "announce", (_, _) -> new byte[] {1});

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.requireReadableBy(List.of(mismatched)))
        .withMessageContaining("announce")
        .withMessageContaining(NoConnectionSettings.class.getName())
        .withMessageContaining(Volume.class.getName());
  }

  private record Volume(double level) {}

  private record VolumeSettings(
      IntegrationConnectionSchema schema, double defaultLevel, boolean wholeLevels)
      implements ConnectionSettingsCapability<Volume> {

    private VolumeSettings(IntegrationConnectionSchema schema, double defaultLevel) {
      this(schema, defaultLevel, true);
    }

    @Override
    public Class<Volume> settingsType() {
      return Volume.class;
    }

    @Override
    public ConnectionSettingsDescriptor descriptor() {
      return new ConnectionSettingsDescriptor(
          List.of(
              new SettingsSection(
                  "audio",
                  "Audio",
                  List.of(new NumberField("volume", "Volume", 0, 10, wholeLevels, defaultLevel)))));
    }

    @Override
    public Volume effective(SettingsDocument complete) {
      return new Volume(complete.number("audio", "volume"));
    }
  }
}
