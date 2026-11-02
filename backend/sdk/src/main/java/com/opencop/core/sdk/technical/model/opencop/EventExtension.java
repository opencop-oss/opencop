// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

import tools.jackson.databind.JsonNode;

public class EventExtension {

  private String namespace;
  private String schema; // URL as string (optional)
  private JsonNode content; // arbitrary JSON

  public EventExtension() {}

  public EventExtension(String namespace, String schema, JsonNode content) {
    this.namespace = namespace;
    this.schema = schema;
    this.content = content;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public JsonNode getContent() {
    return content;
  }

  public void setContent(JsonNode content) {
    this.content = content;
  }

  public static class Builder {
    private String namespace;
    private String schema;
    private JsonNode content;

    public Builder namespace(String v) {
      this.namespace = v;
      return this;
    }

    public Builder schema(String v) {
      this.schema = v;
      return this;
    }

    public Builder content(JsonNode v) {
      this.content = v;
      return this;
    }

    public EventExtension build() {
      return new EventExtension(namespace, schema, content);
    }
  }
}
