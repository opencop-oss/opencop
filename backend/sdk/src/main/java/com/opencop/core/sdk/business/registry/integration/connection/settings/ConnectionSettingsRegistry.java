// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.settings;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsSection;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolationCode;
import com.opencop.core.sdk.business.policy.integration.connection.settings.SettingsFieldPolicy;
import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsCapability;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable registry of every integration's connection settings, indexed by connection schema
 * version, and the host pipeline that resolves a connection's settings.
 *
 * <p>Construction rejects a misconfigured plugin before the application starts: a capability must
 * belong to a registered schema and be the only one for it, every deployment default must satisfy
 * its declared constraint, and the defaults alone must resolve to effective settings of the
 * declared type. Callers never see a settings type; they pass resolved values straight to the
 * behavior registry.
 */
public final class ConnectionSettingsRegistry {

  private static final String DEFAULTS_OF_SCHEMA =
      "Connection settings defaults of connection schema ";

  private final Map<IntegrationKey, IntegrationConnectionSchema> schemas;
  private final Map<IntegrationKey, Registration> registrations;

  public ConnectionSettingsRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<? extends ConnectionSettingsCapability<?>> capabilities) {
    Objects.requireNonNull(schemas, "schemas");
    Objects.requireNonNull(capabilities, "capabilities");

    var registeredSchemas = new HashMap<IntegrationKey, IntegrationConnectionSchema>();
    for (var schema : schemas) {
      if (registeredSchemas.putIfAbsent(IntegrationKey.from(schema), schema) != null) {
        throw new IllegalArgumentException(
            "Duplicate connection schema: " + IntegrationKey.from(schema));
      }
    }
    this.schemas = Map.copyOf(registeredSchemas);

    var indexed = new HashMap<IntegrationKey, Registration>();
    for (var capability : capabilities) {
      Objects.requireNonNull(capability, "capability");
      var key = IntegrationKey.from(capability.schema());
      if (!this.schemas.containsKey(key)) {
        throw new IllegalArgumentException(
            "Unregistered connection schema for settings capability: " + key);
      }
      if (indexed.putIfAbsent(key, Registration.of(capability, key)) != null) {
        throw new IllegalArgumentException(
            "Multiple connection settings capabilities for connection schema: " + key);
      }
    }
    registrations = Map.copyOf(indexed);
  }

  /** Resolves one exact registered connection schema from a persisted reference. */
  public Optional<IntegrationConnectionSchema> find(
      IntegrationConnectionSchemaReference reference) {
    Objects.requireNonNull(reference, "reference");
    return Optional.ofNullable(
        schemas.get(new IntegrationKey(reference.integrationId(), reference.schemaVersion())));
  }

  /**
   * Returns the schema's declared settings, or an empty descriptor when it declares none.
   *
   * @throws IllegalArgumentException when the schema is unknown
   */
  public ConnectionSettingsDescriptor descriptor(IntegrationConnectionSchema schema) {
    return registration(schema)
        .map(registration -> registration.capability().descriptor())
        .orElseGet(ConnectionSettingsDescriptor::empty);
  }

  /**
   * Returns the effective settings of a connection of the schema that overrides nothing, or {@link
   * NoConnectionSettings#INSTANCE} when the schema declares no connection settings.
   *
   * @throws IllegalArgumentException when the schema is unknown
   */
  public Object defaults(IntegrationConnectionSchema schema) {
    return registration(schema).map(Registration::defaults).orElse(NoConnectionSettings.INSTANCE);
  }

  /**
   * Resolves a connection's overrides, given as a parsed JSON object of plain values, into its
   * effective settings.
   *
   * <p>Unknown keys, nulls, wrong types and constraint breaches are all collected. Only overrides
   * that pass every field check are overlaid on the defaults and handed to the integration for its
   * cross-field rules.
   *
   * @throws IllegalArgumentException when the schema is unknown
   */
  public SettingsResolution resolve(IntegrationConnectionSchema schema, Map<String, ?> overrides) {
    Objects.requireNonNull(overrides, "overrides");
    return registration(schema)
        .map(registration -> registration.resolve(overrides))
        .orElseGet(() -> withoutSettings(overrides));
  }

  /**
   * Resolves the overrides a connection stores, if any, into its effective settings. A connection
   * that stores none resolves to the defaults.
   *
   * @throws IllegalArgumentException when the schema is unknown
   */
  public SettingsResolution resolve(
      IntegrationConnectionSchema schema, Optional<SettingsOverrides> stored) {
    Objects.requireNonNull(stored, "stored");
    return resolve(schema, stored.map(SettingsOverrides::sections).orElseGet(Map::of));
  }

  /**
   * Resolves a connection's overrides for the schema version it references. A schema version this
   * runtime does not register declares no settings here, exactly like one without the capability;
   * rejecting the selection itself is left to its caller.
   */
  public SettingsResolution resolve(
      IntegrationConnectionSchemaReference reference, Map<String, ?> overrides) {
    Objects.requireNonNull(overrides, "overrides");
    return find(reference)
        .map(schema -> resolve(schema, overrides))
        .orElseGet(() -> withoutSettings(overrides));
  }

  /** A schema that declares no settings takes none: only an empty document resolves. */
  private static SettingsResolution withoutSettings(Map<String, ?> overrides) {
    return overrides.isEmpty()
        ? new SettingsResolution.Resolved(
            SettingsDocument.empty(), SettingsDocument.empty(), NoConnectionSettings.INSTANCE)
        : rejected(SettingsViolation.notSupported());
  }

  /**
   * Requires every behavior contribution to read the settings type its schema resolves to, so a
   * plugin whose behaviors and settings disagree fails at startup rather than on first connect.
   *
   * @throws IllegalArgumentException when a contribution reads another settings type
   */
  public void requireReadableBy(List<? extends ConnectionBehaviorContribution<?>> contributions) {
    Objects.requireNonNull(contributions, "contributions");
    for (var contribution : contributions) {
      var expected = settingsType(contribution.schema());
      if (!expected.equals(contribution.settingsType())) {
        throw new IllegalArgumentException(
            "Behavior '"
                + contribution.name()
                + "' for connection schema "
                + IntegrationKey.from(contribution.schema())
                + " reads "
                + contribution.settingsType().getName()
                + " settings, but the schema's settings are "
                + expected.getName());
      }
    }
  }

  /**
   * Returns the effective settings type the schema resolves to, {@link NoConnectionSettings} when
   * it declares no settings.
   *
   * @throws IllegalArgumentException when the schema is unknown
   */
  public Class<?> settingsType(IntegrationConnectionSchema schema) {
    return registration(schema)
        .<Class<?>>map(registration -> registration.capability().settingsType())
        .orElse(NoConnectionSettings.class);
  }

  private Optional<Registration> registration(IntegrationConnectionSchema schema) {
    var key = IntegrationKey.from(schema);
    if (!schemas.containsKey(key)) {
      throw new IllegalArgumentException("Unknown connection schema: " + key);
    }
    return Optional.ofNullable(registrations.get(key));
  }

  private static SettingsResolution.Rejected rejected(SettingsViolation violation) {
    return new SettingsResolution.Rejected(List.of(violation));
  }

  /** One capability with the effective settings its deployment defaults resolve to. */
  private record Registration(ConnectionSettingsCapability<?> capability, Object defaults) {

    private static Registration of(ConnectionSettingsCapability<?> capability, IntegrationKey key) {
      var descriptor = Objects.requireNonNull(capability.descriptor(), "descriptor");
      requireDefaultsWithinConstraints(descriptor, key);
      try {
        var defaults = capability.effective(descriptor.defaults());
        requireSettingsType(capability, defaults, key);
        return new Registration(capability, defaults);
      } catch (ConnectionSettingsException exception) {
        throw new IllegalArgumentException(
            DEFAULTS_OF_SCHEMA + key + " break the integration's rules: " + exception.getMessage(),
            exception);
      }
    }

    private SettingsResolution resolve(Map<String, ?> overrides) {
      var descriptor = capability.descriptor();
      var violations = new ArrayList<SettingsViolation>();
      var accepted = new HashMap<String, Map<String, Object>>();
      overrides.forEach(
          (sectionName, sectionValue) ->
              readSection(descriptor, sectionName, sectionValue, accepted, violations));
      if (!violations.isEmpty()) {
        return new SettingsResolution.Rejected(violations);
      }
      var normalised = normalise(descriptor, accepted);
      var complete = overlay(descriptor.defaults(), normalised);
      try {
        return new SettingsResolution.Resolved(
            normalised, complete, capability.effective(complete));
      } catch (ConnectionSettingsException exception) {
        return new SettingsResolution.Rejected(exception.violations());
      }
    }

    private static void readSection(
        ConnectionSettingsDescriptor descriptor,
        String sectionName,
        Object sectionValue,
        Map<String, Map<String, Object>> accepted,
        List<SettingsViolation> violations) {
      var section = descriptor.section(sectionName);
      if (section.isEmpty()) {
        violations.add(
            new SettingsViolation(
                sectionName, SettingsViolationCode.UNKNOWN_SECTION, "is not a settings section"));
        return;
      }
      if (sectionValue == null) {
        violations.add(
            new SettingsViolation(
                sectionName, SettingsViolationCode.NULL_VALUE, "must be omitted rather than null"));
        return;
      }
      if (!(sectionValue instanceof Map<?, ?> fields)) {
        violations.add(
            new SettingsViolation(
                sectionName, SettingsViolationCode.INVALID_TYPE, "must be an object"));
        return;
      }
      fields.forEach(
          (fieldName, raw) ->
              readField(
                  section.orElseThrow(), String.valueOf(fieldName), raw, accepted, violations));
    }

    private static void readField(
        SettingsSection section,
        String fieldName,
        Object raw,
        Map<String, Map<String, Object>> accepted,
        List<SettingsViolation> violations) {
      var path = SettingsField.path(section.name(), fieldName);
      var field = section.field(fieldName);
      if (field.isEmpty()) {
        violations.add(
            new SettingsViolation(
                path, SettingsViolationCode.UNKNOWN_FIELD, "is not a settings field"));
        return;
      }
      var reading = SettingsFieldPolicy.read(field.orElseThrow(), path, raw);
      if (!reading.isAccepted()) {
        violations.add(reading.violation());
        return;
      }
      accepted
          .computeIfAbsent(section.name(), _ -> new HashMap<>())
          .put(fieldName, reading.value());
    }

    /** Orders overrides as declared and drops sections left empty. */
    private static SettingsDocument normalise(
        ConnectionSettingsDescriptor descriptor, Map<String, Map<String, Object>> accepted) {
      var normalised = new LinkedHashMap<String, Map<String, Object>>();
      for (var section : descriptor.sections()) {
        var values = accepted.getOrDefault(section.name(), Map.of());
        var fields = new LinkedHashMap<String, Object>();
        for (var field : section.fields()) {
          if (values.containsKey(field.name())) {
            fields.put(field.name(), values.get(field.name()));
          }
        }
        if (!fields.isEmpty()) {
          normalised.put(section.name(), fields);
        }
      }
      return new SettingsDocument(normalised);
    }

    /** Replaces each default with its override, field by field. */
    private static SettingsDocument overlay(SettingsDocument defaults, SettingsDocument overrides) {
      var complete = new LinkedHashMap<String, Map<String, Object>>();
      defaults
          .sections()
          .forEach(
              (section, fields) -> {
                var merged = new LinkedHashMap<String, Object>(fields);
                merged.putAll(overrides.sections().getOrDefault(section, Map.of()));
                complete.put(section, merged);
              });
      return new SettingsDocument(complete);
    }

    private static void requireDefaultsWithinConstraints(
        ConnectionSettingsDescriptor descriptor, IntegrationKey key) {
      var violations = new ArrayList<SettingsViolation>();
      for (var section : descriptor.sections()) {
        for (var field : section.fields()) {
          SettingsFieldPolicy.check(
                  field, SettingsField.path(section.name(), field.name()), field.defaultValue())
              .ifPresent(violations::add);
        }
      }
      if (!violations.isEmpty()) {
        throw new IllegalArgumentException(
            DEFAULTS_OF_SCHEMA
                + key
                + " break their declared constraints: "
                + violations.stream()
                    .map(violation -> violation.path() + " " + violation.message())
                    .collect(Collectors.joining("; ")));
      }
    }

    private static void requireSettingsType(
        ConnectionSettingsCapability<?> capability, Object defaults, IntegrationKey key) {
      var settingsType = Objects.requireNonNull(capability.settingsType(), "settingsType");
      if (!settingsType.isInstance(defaults)) {
        throw new IllegalArgumentException(
            "Connection settings of connection schema "
                + key
                + " must resolve to a "
                + settingsType.getName());
      }
    }
  }

  private record IntegrationKey(UUID id, int schemaVersion) {

    private static IntegrationKey from(IntegrationConnectionSchema schema) {
      Objects.requireNonNull(schema, "schema");
      Objects.requireNonNull(schema.integration(), "schema.integration");
      return new IntegrationKey(schema.integration().id(), schema.schemaVersion());
    }
  }
}
