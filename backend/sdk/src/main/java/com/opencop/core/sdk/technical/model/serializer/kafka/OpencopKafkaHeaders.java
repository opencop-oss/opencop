// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer.kafka;

public enum OpencopKafkaHeaders {
  MAPPER_ID("opencop-mapper-id"),
  IMPORT_ID("opencop-import-id"),
  SOURCE_ID("opencop-source-id"),
  OBJECT_ID("opencop-object-id"),
  OPENCOP_TYPE("opencop-type"),
  ICON_REFS("opencop-icon-refs"),
  LAYER_ID("opencop-layer-id"),
  CONTENT_KIND("opencop-content-kind");

  private final String name;

  OpencopKafkaHeaders(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
