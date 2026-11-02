// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.source;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = XPathSource.class, name = "xpath"),
  @JsonSubTypes.Type(value = JsonPathSource.class, name = "jsonpath"),
  @JsonSubTypes.Type(value = CsvColumnSource.class, name = "csvColumn"),
  @JsonSubTypes.Type(value = ConstSource.class, name = "const")
})
public sealed interface SourceRef
    permits XPathSource, JsonPathSource, CsvColumnSource, ConstSource {}
