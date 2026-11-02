// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.external;

import org.springframework.http.HttpStatusCode;

public class ExternalServiceException extends Exception {

  private final HttpStatusCode statusCode;
  private final String endpoint;
  private final String responseBody;

  public ExternalServiceException(
      String message, HttpStatusCode statusCode, String endpoint, String responseBody) {
    super(message);
    this.statusCode = statusCode;
    this.endpoint = endpoint;
    this.responseBody = responseBody;
  }

  public HttpStatusCode getStatusCode() {
    return statusCode;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getResponseBody() {
    return responseBody;
  }

  @Override
  public String toString() {
    return "ExternalServiceException{"
        + "statusCode="
        + statusCode
        + ", endpoint='"
        + endpoint
        + '\''
        + ", responseBody='"
        + responseBody
        + '\''
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}
