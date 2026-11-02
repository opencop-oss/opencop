// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ExportFormatResolver {

  private static final String UNSUPPORTED_FORMAT_MESSAGE = "Unsupported export format";

  private final List<FeatureExportMapper> mappers;

  public ExportFormatResolver(List<FeatureExportMapper> mappers) {
    this.mappers = List.copyOf(mappers);
  }

  public FeatureExportMapper mapperFor(ExportRequest.Format format) {
    return mappers.stream()
        .filter(mapper -> mapper.format() == format)
        .findFirst()
        .orElseThrow(
            () -> new GlobalOpenCopException(UNSUPPORTED_FORMAT_MESSAGE, HttpStatus.BAD_REQUEST));
  }

  public MediaType contentTypeFor(String storedFormat) {
    return mapperFor(parseStoredFormat(storedFormat)).contentType();
  }

  private static ExportRequest.Format parseStoredFormat(String storedFormat) {
    try {
      return ExportRequest.Format.valueOf(storedFormat);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new GlobalOpenCopException(
          UNSUPPORTED_FORMAT_MESSAGE, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
