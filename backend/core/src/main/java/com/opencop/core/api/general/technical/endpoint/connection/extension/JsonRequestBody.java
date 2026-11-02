// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.extension;

import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionRequestBody;
import com.opencop.core.api.general.ports.usecase.connection.extension.UnreadableExtensionBodyException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** A request's JSON body, read only once the operation's body type is known. */
final class JsonRequestBody implements ExtensionRequestBody {

  private final HttpServletRequest request;
  private final ObjectMapper mapper;

  JsonRequestBody(HttpServletRequest request, ObjectMapper mapper) {
    this.request = request;
    this.mapper = mapper;
  }

  @Override
  public <T> T read(Class<T> type) {
    byte[] content;
    try {
      content = request.getInputStream().readAllBytes();
    } catch (IOException e) {
      throw new UnreadableExtensionBodyException("The request body cannot be read", e);
    }
    T value;
    try {
      value = content.length == 0 ? null : mapper.readValue(content, type);
    } catch (JacksonException e) {
      throw new UnreadableExtensionBodyException(
          "The request body is not valid JSON for its type", e);
    }
    if (value == null) {
      throw new UnreadableExtensionBodyException("The request has no body", null);
    }
    return value;
  }
}
