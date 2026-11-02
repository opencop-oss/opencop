// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.BooleanField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.ChoiceField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.DurationField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.NumberField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.TextField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsSection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape of the settings a connection to one integration schema version may override.
 *
 * <p>Every name here is provider-neutral: a client renders this without hardcoded knowledge of any
 * integration, so no field, branch or type may name one.
 */
@Schema(
    name = "IntegrationConnectionSettingsResponse",
    description =
        """
        Everything needed to render a connection settings form for one integration schema \
        version, without hardcoded knowledge of the integration.

        Render one group per entry of `sections` and that section's `fields` in the order \
        given, choosing the form component from each field's `kind`. Every field is optional: \
        omitting it from a connection's settings inherits `default`, the current deployment \
        value. Constraints are enforced on every write, so a client may use them to validate \
        early. Durations are ISO-8601 strings.""")
public record IntegrationConnectionSettingsResponse(
    @Schema(description = "Identity of the integration these settings belong to")
        UUID integrationId,
    @Schema(description = "Schema version of the integration; part of its composite identity")
        int schemaVersion,
    @Schema(
            description =
                "Settings sections in display order. Empty when the integration declares no"
                    + " connection settings.")
        List<SectionResponse> sections) {

  static IntegrationConnectionSettingsResponse from(
      UUID integrationId, int schemaVersion, ConnectionSettingsDescriptor descriptor) {
    return new IntegrationConnectionSettingsResponse(
        integrationId,
        schemaVersion,
        descriptor.sections().stream().map(SectionResponse::from).toList());
  }

  @Schema(name = "ConnectionSettingsSection")
  public record SectionResponse(
      @Schema(description = "Section key in a connection's settings document") String name,
      @Schema(description = "Display label") String label,
      @Schema(description = "Fields in display order") List<FieldResponse> fields) {

    static SectionResponse from(SettingsSection section) {
      return new SectionResponse(
          section.name(),
          section.label(),
          section.fields().stream().map(FieldResponse::from).toList());
    }
  }

  /**
   * One settings field. Only the constraint members of the field's kind are present: {@code
   * maxLength} for TEXT, {@code min}, {@code max} and {@code integer} for NUMBER, {@code min} and
   * {@code max} for DURATION, {@code options} for CHOICE.
   */
  @Schema(name = "ConnectionSettingsField")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record FieldResponse(
      @Schema(description = "Field key within its section") String name,
      @Schema(description = "Value type; decides the form component and the wire form")
          SettingsFieldKind kind,
      @Schema(description = "Display label") String label,
      @Schema(description = "TEXT: longest accepted value. Text must also not be blank.")
          Integer maxLength,
      @Schema(
              description = "NUMBER: inclusive lower bound. DURATION: inclusive ISO-8601 bound.",
              oneOf = {Number.class, String.class})
          Object min,
      @Schema(
              description = "NUMBER: inclusive upper bound. DURATION: inclusive ISO-8601 bound.",
              oneOf = {Number.class, String.class})
          Object max,
      @Schema(description = "NUMBER: whether only whole numbers are accepted") Boolean integer,
      @Schema(description = "CHOICE: the accepted values, compared exactly") List<String> options,
      @JsonProperty("default")
          @JsonInclude(JsonInclude.Include.ALWAYS)
          @Schema(
              description =
                  "Current deployment value, inherited when the field is omitted: a string for"
                      + " TEXT, CHOICE and DURATION, a number for NUMBER, a boolean for BOOLEAN",
              oneOf = {String.class, Number.class, Boolean.class})
          Object defaultValue) {

    static FieldResponse from(SettingsField field) {
      return switch (field) {
        case TextField text ->
            new FieldResponse(
                text.name(),
                text.kind(),
                text.label(),
                text.maxLength(),
                null,
                null,
                null,
                null,
                text.defaultValue());
        case NumberField number ->
            new FieldResponse(
                number.name(),
                number.kind(),
                number.label(),
                null,
                wire(number.min()),
                wire(number.max()),
                number.integer(),
                null,
                wire(number.defaultValue()));
        case BooleanField flag ->
            new FieldResponse(
                flag.name(),
                flag.kind(),
                flag.label(),
                null,
                null,
                null,
                null,
                null,
                flag.defaultValue());
        case DurationField duration ->
            new FieldResponse(
                duration.name(),
                duration.kind(),
                duration.label(),
                null,
                wire(duration.min()),
                wire(duration.max()),
                null,
                null,
                wire(duration.defaultValue()));
        case ChoiceField choice ->
            new FieldResponse(
                choice.name(),
                choice.kind(),
                choice.label(),
                null,
                null,
                null,
                null,
                choice.options(),
                choice.defaultValue());
      };
    }

    /** Writes a whole number without a fraction, so integer bounds read as integers. */
    private static Number wire(double value) {
      return SettingsDocument.wireNumber(value);
    }

    private static String wire(Duration value) {
      return value.toString();
    }
  }
}
