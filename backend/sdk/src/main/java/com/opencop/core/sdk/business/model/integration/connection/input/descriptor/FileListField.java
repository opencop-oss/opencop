// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.Optional;
import java.util.Set;

/**
 * A field carrying one or more uploaded files supplied under the same field name.
 *
 * @param name payload key the files are supplied under, which is also their multipart role
 * @param label display text for the field
 * @param required whether at least one file must be supplied
 * @param extensions accepted filename extensions, lowercase and dot-prefixed, at least one
 * @param mediaTypes accepted media types, optional
 * @param standard the shared field this declares, when it is one; a client localizes it by its key
 */
public record FileListField(
    String name,
    String label,
    boolean required,
    Set<String> extensions,
    Set<String> mediaTypes,
    Optional<StandardExtractorField> standard)
    implements ExtractorField {

  public FileListField {
    name = ExtractorFieldSyntax.requireText(name, "name");
    label = ExtractorFieldSyntax.requireText(label, "label");
    extensions = ExtractorFieldSyntax.copyExtensions(extensions, name);
    mediaTypes = ExtractorFieldSyntax.copyMediaTypes(mediaTypes);
    ExtractorFieldSyntax.requireMatchingStandard(
        standard, name, label, ExtractorFieldKind.FILE_LIST, extensions, mediaTypes);
  }

  /** Declares a field no other integration shares. */
  public FileListField(
      String name, String label, boolean required, Set<String> extensions, Set<String> mediaTypes) {
    this(name, label, required, extensions, mediaTypes, Optional.empty());
  }

  @Override
  public ExtractorFieldKind kind() {
    return ExtractorFieldKind.FILE_LIST;
  }
}
