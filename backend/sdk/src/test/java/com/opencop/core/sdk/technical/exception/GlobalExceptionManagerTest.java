// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.exception.ErrorDetail;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionManagerTest {

  private final GlobalExceptionManager manager = new GlobalExceptionManager();
  private final JsonMapper mapper = JsonMapper.builder().build();

  /** A client highlights the rejected input from the detail code and the field it names. */
  @Test
  void answersWithTheDetailAndFieldTheExceptionCarries() {
    var exception =
        new DetailedException(
            "INPUT_INVALID", "Listen port is invalid", "INVALID_PORT", "port", List.of());

    var response = manager.handleGlobalOpenCopException(exception, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .isEqualTo(
            new GlobalErrorResponse(
                "INPUT_INVALID", "Listen port is invalid", "INVALID_PORT", "port", null));
  }

  @Test
  void omitsTheDetailAndFieldWhenTheExceptionCarriesNone() {
    var response =
        manager.handleGlobalOpenCopException(
            new GlobalOpenCopException("CODE", "Message", HttpStatus.CONFLICT), null);

    assertThat(mapper.writeValueAsString(response.getBody()))
        .isEqualTo("{\"code\":\"CODE\",\"message\":\"Message\"}");
  }

  /** Any route enriches its error with details instead of new response fields. */
  @Test
  void listsTheDetailsAnyExceptionAttaches() {
    var exception =
        new GlobalOpenCopException(
            "CODE",
            "Message",
            HttpStatus.CONFLICT,
            List.of(
                new ErrorDetail("LIMIT_EXCEEDED", "Too many", "items", Map.of("max", 128)),
                new ErrorDetail("ALREADY_EXISTS", "Name taken", null, null)));

    var response = manager.handleGlobalOpenCopException(exception, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(mapper.writeValueAsString(response.getBody()))
        .isEqualTo(
            "{\"code\":\"CODE\",\"message\":\"Message\",\"details\":["
                + "{\"code\":\"LIMIT_EXCEEDED\",\"message\":\"Too many\",\"target\":\"items\","
                + "\"metadata\":{\"max\":128}},"
                + "{\"code\":\"ALREADY_EXISTS\",\"message\":\"Name taken\"}]}");
  }

  @Test
  void aSubclassCarriesTheDetailsItIsBuiltWithUnchangeable() {
    var exception =
        new DetailedException(
            "CODE",
            "Message",
            "DETAIL",
            "field",
            List.of(
                new ErrorDetail("FIRST", "First", null, null),
                new ErrorDetail("SECOND", "Second", null, null)));

    assertThat(exception.getDetails())
        .extracting(ErrorDetail::code)
        .containsExactly("FIRST", "SECOND");
    assertThatThrownBy(() -> exception.getDetails().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  /** A connection failure carries no SQL state; it is still answered as an internal error. */
  @Test
  void answersAFailureCausedByASqlErrorWithoutAStateAsAnInternalError() {
    var failure =
        new IllegalStateException("Unable to commit", new SQLException("I/O error", (String) null));

    var response = manager.handleInternalServer(failure, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void answersALifecycleTriggerRejectionAsAConflict() {
    var failure =
        new IllegalStateException("Unable to commit", new SQLException("rejected", "23U01"));

    var response = manager.handleInternalServer(failure, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("LIFECYCLE_CONFLICT");
  }

  /** A body the endpoint does not read is the client's mistake, not a server failure. */
  @Test
  void answersAnUnsupportedContentTypeAsUnsupportedMediaType() {
    var exception =
        new HttpMediaTypeNotSupportedException(
            MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

    var response = manager.handleUnsupportedMediaType(exception, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }

  /** A method the route does not serve is the client's mistake, not a server failure. */
  @Test
  void answersAMethodTheRouteDoesNotServeAsMethodNotAllowed() {
    var exception = new HttpRequestMethodNotSupportedException("POST", List.of("GET", "DELETE"));

    var response = manager.handleMethodNotSupported(exception, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getHeaders().getAllow())
        .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
    assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
  }

  private static final class DetailedException extends GlobalOpenCopException {

    DetailedException(
        String code, String message, String detailCode, String field, List<ErrorDetail> details) {
      super(code, message, HttpStatus.BAD_REQUEST, detailCode, field, details);
    }
  }
}
