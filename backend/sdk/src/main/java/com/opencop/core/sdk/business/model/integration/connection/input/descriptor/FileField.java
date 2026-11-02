// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.Optional;
import java.util.Set;

/**
 * A field carrying exactly one uploaded file.
 *
 * <p>Formats are declared as two flat sets, because real formats are not a one-to-one pairing: one
 * extension may be served under several media types, one media type may cover several extensions,
 * and some extensions have no registered media type at all. A client concatenates both into a
 * single HTML {@code accept} list, which takes extensions and media types in the same
 * comma-separated value.
 *
 * @param name payload key the file is supplied under, which is also its multipart role
 * @param label display text for the field
 * @param required whether the field must be supplied
 * @param extensions accepted filename extensions, lowercase and dot-prefixed, at least one
 * @param mediaTypes accepted media types, optional
 * @param standard the shared field this declares, when it is one; a client localizes it by its key
 */
public record FileField(
    String name,
    String label,
    boolean required,
    Set<String> extensions,
    Set<String> mediaTypes,
    Optional<StandardExtractorField> standard)
    implements ExtractorField {

  public FileField {
    name = ExtractorFieldSyntax.requireText(name, "name");
    label = ExtractorFieldSyntax.requireText(label, "label");
    extensions = ExtractorFieldSyntax.copyExtensions(extensions, name);
    mediaTypes = ExtractorFieldSyntax.copyMediaTypes(mediaTypes);
    ExtractorFieldSyntax.requireMatchingStandard(
        standard, name, label, ExtractorFieldKind.FILE, extensions, mediaTypes);
  }

  /** Declares a field no other integration shares. */
  public FileField(
      String name, String label, boolean required, Set<String> extensions, Set<String> mediaTypes) {
    this(name, label, required, extensions, mediaTypes, Optional.empty());
  }

  @Override
  public ExtractorFieldKind kind() {
    return ExtractorFieldKind.FILE;
  }
}
