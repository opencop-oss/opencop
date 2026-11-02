// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import com.unboundid.scim2.common.BaseScimResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ScimHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      List.of(MediaType.valueOf("application/scim+json"), MediaType.APPLICATION_JSON);

  private final ObjectMapper mapper;

  ScimHttpMessageConverter() {
    super(SUPPORTED_MEDIA_TYPES.toArray(MediaType[]::new));
    BaseScimResource.IGNORE_UNKNOWN_FIELDS = false;
    mapper = JsonUtils.createObjectMapper();
  }

  @Override
  protected boolean supports(@NonNull Class<?> clazz) {
    return true;
  }

  @Override
  protected Object readInternal(@NonNull Class<?> clazz, @NonNull HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    try {
      return mapper.readValue(inputMessage.getBody(), clazz);
    } catch (JacksonException e) {
      throw new HttpMessageNotReadableException(e.getMessage(), e, inputMessage);
    }
  }

  @Override
  protected void writeInternal(Object body, @NonNull HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    try {
      mapper.writeValue(outputMessage.getBody(), body);
    } catch (JacksonException e) {
      throw new HttpMessageNotWritableException("Could not write SCIM JSON", e);
    }
  }
}
