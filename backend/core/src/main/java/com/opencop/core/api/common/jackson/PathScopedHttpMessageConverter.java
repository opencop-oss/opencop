// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.jackson;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class PathScopedHttpMessageConverter implements HttpMessageConverter<Object> {

  private final HttpMessageConverter<Object> delegate;
  private final Predicate<HttpServletRequest> matches;

  public PathScopedHttpMessageConverter(
      HttpMessageConverter<Object> delegate, Predicate<HttpServletRequest> matches) {
    this.delegate = delegate;
    this.matches = matches;
  }

  @Override
  public boolean canRead(@NonNull Class<?> clazz, MediaType mediaType) {
    HttpServletRequest req = currentRequest();
    if (req == null || !matches.test(req)) {
      return false;
    }
    return delegate.canRead(clazz, mediaType);
  }

  @Override
  public boolean canWrite(@NonNull Class<?> clazz, MediaType mediaType) {
    HttpServletRequest req = currentRequest();
    if (req == null || !matches.test(req)) {
      return false; // <-- prevents write() being called on other paths
    }
    return delegate.canWrite(clazz, mediaType);
  }

  @Override
  public @NullMarked List<MediaType> getSupportedMediaTypes() {
    return delegate.getSupportedMediaTypes();
  }

  @Override
  public Object read(@NonNull Class<?> clazz, @NonNull HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return delegate.read(clazz, inputMessage);
  }

  @Override
  public void write(Object body, MediaType contentType, @NonNull HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    delegate.write(body, contentType, outputMessage);
  }

  private static HttpServletRequest currentRequest() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getRequest();
    }
    return null;
  }
}
