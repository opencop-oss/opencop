// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fields that more than one integration means the same way, declared once so every extractor and
 * the host spell, type, and read them identically.
 *
 * <p>A client localizes a standard field by its key, this constant, and shows its label only when
 * it has no translation, so a standard field carries the default label and no extractor rewords it.
 * A field only one integration understands is declared with its own name instead.
 *
 * <p>The wire names are contract values that clients already send as values and multipart roles;
 * they are kept as they are, inconsistent casing included.
 */
public enum StandardExtractorField {
  HOST("host", ExtractorFieldKind.TEXT, "Host", Set.of()),
  PORT("port", ExtractorFieldKind.TEXT, "Port", Set.of()),
  CLIENT_CERTIFICATE_STORE(
      "keyStore", ExtractorFieldKind.FILE, "Client certificate store", Formats.CERTIFICATE_STORE),
  CLIENT_CERTIFICATE_PASSWORD(
      "clientPassword", ExtractorFieldKind.SECRET, "Client certificate password", Set.of()),
  TRUSTSTORE("truststore", ExtractorFieldKind.FILE, "Truststore", Formats.CERTIFICATE_STORE),
  TRUSTSTORE_PASSWORD(
      "truststorePassword", ExtractorFieldKind.SECRET, "Truststore password", Set.of());

  private final String fieldName;
  private final ExtractorFieldKind kind;
  private final String defaultLabel;
  private final Set<String> extensions;

  StandardExtractorField(
      String fieldName, ExtractorFieldKind kind, String defaultLabel, Set<String> extensions) {
    this.fieldName = fieldName;
    this.kind = kind;
    this.defaultLabel = defaultLabel;
    this.extensions = extensions;
  }

  /** Returns the payload key the field is supplied under, which is also its multipart role. */
  public String fieldName() {
    return fieldName;
  }

  public ExtractorFieldKind kind() {
    return kind;
  }

  /**
   * Whether a field with these properties is this standard field exactly: a client renders it from
   * the key alone, so a standard field differing in any of them would be shown wrongly.
   */
  boolean isDeclaredAs(
      String name,
      String label,
      ExtractorFieldKind fieldKind,
      Set<String> fieldExtensions,
      Set<String> mediaTypes) {
    return fieldName.equals(name)
        && defaultLabel.equals(label)
        && kind == fieldKind
        && extensions.equals(fieldExtensions)
        && mediaTypes.isEmpty();
  }

  /** Declares this field for a descriptor, with the standard name, kind, label, and formats. */
  public ExtractorField declare(boolean required) {
    var standard = Optional.of(this);
    return switch (kind) {
      case TEXT -> new TextField(fieldName, defaultLabel, required, standard);
      case SECRET -> new SecretField(fieldName, defaultLabel, required, standard);
      case FILE -> new FileField(fieldName, defaultLabel, required, extensions, Set.of(), standard);
      case FILE_LIST ->
          new FileListField(fieldName, defaultLabel, required, extensions, Set.of(), standard);
    };
  }

  /** Describes the standard declaration, for a rejection naming what was expected. */
  String describe() {
    return name() + " (" + kind + " field '" + fieldName + "', label '" + defaultLabel + "')";
  }

  /** The standard field declared under the wire name, if any. */
  public static Optional<StandardExtractorField> named(String fieldName) {
    return Arrays.stream(values()).filter(field -> field.fieldName.equals(fieldName)).findFirst();
  }

  /** Returns the value the input supplied for this field, if any. */
  public Optional<String> valueIn(ConnectionInput input) {
    return input.value(fieldName);
  }

  /** Returns the resources the input supplied for this field. */
  public List<ConnectionInputResource> resourcesIn(ConnectionInput input) {
    return input.resources(fieldName);
  }

  /**
   * Accepted formats shared by several standard fields; an enum's own constants cannot hold them.
   */
  private static final class Formats {

    private static final Set<String> CERTIFICATE_STORE = Set.of(".p12", ".pfx");

    private Formats() {}
  }
}
