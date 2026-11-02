// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import com.opencop.core.sdk.ports.exception.ExceptionCode;
import org.springframework.http.HttpStatus;

public enum GeocodingExceptionCodes implements ExceptionCode {
  INVALID_REQUEST("GEOCODING-1000", "Invalid geocoding request", HttpStatus.BAD_REQUEST),
  GEOCODER_NOT_FOUND("GEOCODING-1001", "Geocoder not found", HttpStatus.NOT_FOUND),
  PROVIDER_FAILED(
      "GEOCODING-1002", "The geocoding provider could not answer", HttpStatus.BAD_GATEWAY),
  PROVIDER_TIMED_OUT(
      "GEOCODING-1003",
      "The geocoding provider did not answer in time",
      HttpStatus.GATEWAY_TIMEOUT),
  PROVIDER_RATE_LIMITED(
      "GEOCODING-1004",
      "The geocoding provider is refusing requests for now",
      HttpStatus.SERVICE_UNAVAILABLE),
  TOO_MANY_REQUESTS(
      "GEOCODING-1005", "Too many geocoding requests; slow down", HttpStatus.TOO_MANY_REQUESTS);

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  GeocodingExceptionCodes(String code, String description, HttpStatus httpStatus) {
    this.code = code;
    this.description = description;
    this.httpStatus = httpStatus;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
