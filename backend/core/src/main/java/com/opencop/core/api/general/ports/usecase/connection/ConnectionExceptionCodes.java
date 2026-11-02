// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection;

import com.opencop.core.sdk.ports.exception.ExceptionCode;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionRecordStore;
import org.springframework.http.HttpStatus;

public enum ConnectionExceptionCodes implements ExceptionCode {
  MISSING_REQUIRED_FILE(
      "CORE-CONNECTION-1001",
      "Required file missing: expected %s for this connection type",
      HttpStatus.BAD_REQUEST),
  BLANK_CONNECTION_NAME(
      "CORE-CONNECTION-1002", "Connection name must not be blank", HttpStatus.BAD_REQUEST),
  CONNECTION_NAME_ALREADY_EXISTS(
      "CORE-CONNECTION-1003", "A data source named '%s' already exists", HttpStatus.CONFLICT),
  NO_FIELDS_TO_UPDATE(
      "CORE-CONNECTION-1004",
      "At least one field must be provided to update",
      HttpStatus.BAD_REQUEST),
  INVALID_ACCESS_MODE(
      "CORE-CONNECTION-1005", "Access mode must be one of R, W, or RW", HttpStatus.BAD_REQUEST),
  UNSUPPORTED_ACCESS_MODE(
      "CORE-CONNECTION-1006",
      "Access mode is not supported by the selected connection configuration",
      HttpStatus.BAD_REQUEST),
  LISTEN_PORT_IN_USE(
      "CORE-CONNECTION-1007", "Listen port %d is already in use", HttpStatus.CONFLICT),
  INGRESS_MAPPER_REQUIRED(
      "INGRESS_MAPPER_REQUIRED", "%s access requires an ingress mapper", HttpStatus.BAD_REQUEST),
  EGRESS_MAPPER_REQUIRED(
      "EGRESS_MAPPER_REQUIRED", "%s access requires an egress mapper", HttpStatus.BAD_REQUEST),
  ACCESS_CHANGE_UNSUPPORTED(
      "ACCESS_CHANGE_UNSUPPORTED",
      "Access cannot change from %s to %s: a patch cannot make a connection writable or change a"
          + " writable connection's access",
      HttpStatus.BAD_REQUEST),
  MAPPER_NOT_FOUND(
      "MAPPER_NOT_FOUND", "The selected mapper is unavailable", HttpStatus.BAD_REQUEST),
  MAPPER_ARCHIVED("MAPPER_ARCHIVED", "The selected mapper is archived", HttpStatus.BAD_REQUEST),
  FRAMING_NOT_FOUND(
      "FRAMING_NOT_FOUND", "The selected framing is unavailable", HttpStatus.BAD_REQUEST),
  PAIR_NOT_ALLOWED(
      "PAIR_NOT_ALLOWED",
      "The selected mapper and framing combination is not allowed by this integration schema",
      HttpStatus.BAD_REQUEST),
  INTEGRATION_PATCH_NOT_ALLOWED(
      "INTEGRATION_PATCH_NOT_ALLOWED",
      "Integration cannot be changed through the connection metadata PATCH",
      HttpStatus.BAD_REQUEST),
  INTEGRATION_NOT_FOUND(
      "INTEGRATION_NOT_FOUND",
      "Integration %s schema version %d was not found",
      HttpStatus.NOT_FOUND),
  INTEGRATION_DECLARATION_NOT_FOUND(
      "INTEGRATION_DECLARATION_NOT_FOUND",
      "Integration %s schema version %d has no connection declaration for %s",
      HttpStatus.BAD_REQUEST),
  INTEGRATION_INACTIVE("INTEGRATION_INACTIVE", "Integration %s is inactive", HttpStatus.CONFLICT),
  CONNECTION_NAME_TOO_LONG(
      "CORE-CONNECTION-1011",
      "Connection name must not exceed %d characters",
      HttpStatus.BAD_REQUEST),
  FRAMING_NOT_ALLOWED(
      "FRAMING_NOT_ALLOWED",
      "The selected framing is not allowed by this integration schema",
      HttpStatus.BAD_REQUEST),
  REQUIRED_FIELD_INVALID(
      "REQUIRED_FIELD_INVALID", "Field '%s' is missing or invalid", HttpStatus.BAD_REQUEST),
  UNKNOWN_REQUEST_FIELD(
      "UNKNOWN_REQUEST_FIELD", "Field '%s' is not part of this request", HttpStatus.BAD_REQUEST),
  UNSUPPORTED_CONNECTION_KIND(
      "UNSUPPORTED_CONNECTION_KIND",
      "The connection kind is missing or not supported",
      HttpStatus.BAD_REQUEST),
  CONNECTION_KIND_CHANGE_UNSUPPORTED(
      "CONNECTION_KIND_CHANGE_UNSUPPORTED",
      "A connection's transport kind cannot change; create a new connection instead",
      HttpStatus.BAD_REQUEST),
  CONNECTION_SETUP_NOT_SUPPORTED(
      "CONNECTION_SETUP_NOT_SUPPORTED",
      "The setup of a %s connection cannot be replaced",
      HttpStatus.BAD_REQUEST),
  EXTRACTOR_NOT_FOUND(
      "EXTRACTOR_NOT_FOUND",
      "Integration %s schema version %d has no %s extractor %s",
      HttpStatus.BAD_REQUEST),
  MAPPER_NOT_ALLOWED(
      "MAPPER_NOT_ALLOWED",
      "The selected mapper is not allowed by this integration schema",
      HttpStatus.BAD_REQUEST),
  CONNECTION_INPUT_INVALID("CONNECTION_INPUT_INVALID", "%s", HttpStatus.BAD_REQUEST),
  INVALID_REQUEST_BODY("INVALID_REQUEST", "Invalid request body", HttpStatus.BAD_REQUEST),
  EXTRACTOR_FAILED("INTERNAL_ERROR", "Something went wrong.", HttpStatus.INTERNAL_SERVER_ERROR),
  INGRESS_MAPPER_NOT_ACCEPTED(
      "INGRESS_MAPPER_NOT_ACCEPTED",
      "%s access reads nothing, so it takes no ingress mapper",
      HttpStatus.BAD_REQUEST),
  EGRESS_MAPPER_NOT_ACCEPTED(
      "EGRESS_MAPPER_NOT_ACCEPTED",
      "%s access writes nothing, so it takes no egress mapper",
      HttpStatus.BAD_REQUEST),
  INVALID_CUSTOM_FRAMING("INVALID_CUSTOM_FRAMING", "%s", HttpStatus.BAD_REQUEST),
  BLANK_CONNECTION_INPUT_NAME(
      "BLANK_CONNECTION_INPUT_NAME", "Every value and file must be named", HttpStatus.BAD_REQUEST),
  CONNECTION_INPUT_NAME_CONFLICT(
      "CONNECTION_INPUT_NAME_CONFLICT",
      "'%s' is supplied both as a value and as a file",
      HttpStatus.BAD_REQUEST),
  DUPLICATE_REQUEST_BODY(
      "DUPLICATE_REQUEST_BODY",
      "Only the request's JSON may be named 'body'",
      HttpStatus.BAD_REQUEST),
  SYSTEM_MAPPER_UNAVAILABLE(
      "SYSTEM_MAPPER_UNAVAILABLE",
      "The mapper this connection kind requires is unavailable",
      HttpStatus.INTERNAL_SERVER_ERROR),
  /**
   * The selected integration schema version is catalogued and active, but no implementation of it
   * is registered in the running service.
   */
  INTEGRATION_UNAVAILABLE(
      "INTEGRATION_UNAVAILABLE",
      "Integration %s schema version %d is not available in this runtime",
      HttpStatus.CONFLICT),
  /**
   * The connection's integration settings break one or more rules; every violation is listed in the
   * error's details as {code, message, target}.
   */
  INVALID_CONNECTION_SETTINGS(
      "INVALID_CONNECTION_SETTINGS",
      "The connection's integration settings are invalid",
      HttpStatus.BAD_REQUEST),
  /** The connection's integration declares no extension with the identifier. */
  CONNECTION_EXTENSION_NOT_FOUND(
      "CONNECTION_EXTENSION_NOT_FOUND", "The connection has no extension %s", HttpStatus.NOT_FOUND),
  /** The extension declares no operation answering the request's method and path. */
  CONNECTION_EXTENSION_OPERATION_NOT_FOUND(
      "CONNECTION_EXTENSION_OPERATION_NOT_FOUND",
      "Extension %s declares no operation %s %s",
      HttpStatus.NOT_FOUND),
  /**
   * The settings the connection stores no longer resolve, after a deployment default changed for
   * instance, so its extensions have no effective settings to run with.
   */
  CONNECTION_SETTINGS_UNRESOLVED(
      "CONNECTION_SETTINGS_UNRESOLVED",
      "The connection's stored integration settings no longer resolve",
      HttpStatus.CONFLICT),
  /** An extension operation failed unexpectedly; the failure is logged, never shown. */
  EXTENSION_FAILED(
      "EXTENSION_FAILED", "The extension operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
  /**
   * The connection holds records its extensions wrote for its integration, so it cannot move to
   * another integration until they are removed.
   */
  CONNECTION_RECORDS_PRESENT(
      "CONNECTION_RECORDS_PRESENT",
      "The connection holds extension records written for its current integration",
      HttpStatus.CONFLICT),
  /**
   * A record is not at the revision the write expected: it exists when it should not, is missing,
   * or someone else wrote it first.
   */
  CONNECTION_RECORD_REVISION_CONFLICT(
      ConnectionRecordStore.REVISION_CONFLICT,
      "The record is not at the revision the write expected",
      HttpStatus.CONFLICT),
  /** A record write would exceed what one extension may store on one connection. */
  CONNECTION_RECORD_LIMIT(
      ConnectionRecordStore.LIMIT_EXCEEDED,
      "The extension cannot store more on this connection",
      HttpStatus.CONFLICT),
  /** An extension refused the request as given, without a code of its own. */
  EXTENSION_BAD_REQUEST("EXTENSION_BAD_REQUEST", "%s", HttpStatus.BAD_REQUEST),
  /** Something an extension request names does not exist, without a code of its own. */
  EXTENSION_NOT_FOUND("EXTENSION_NOT_FOUND", "%s", HttpStatus.NOT_FOUND),
  /** An extension request conflicts with the current state, without a code of its own. */
  EXTENSION_CONFLICT("EXTENSION_CONFLICT", "%s", HttpStatus.CONFLICT),
  /** The remote system an extension reached failed, without a code of its own. */
  EXTENSION_UPSTREAM_FAILURE("EXTENSION_UPSTREAM_FAILURE", "%s", HttpStatus.BAD_GATEWAY),
  /** The remote system an extension reached did not answer in time, without a code of its own. */
  EXTENSION_UPSTREAM_TIMEOUT("EXTENSION_UPSTREAM_TIMEOUT", "%s", HttpStatus.GATEWAY_TIMEOUT);

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  ConnectionExceptionCodes(String code, String description, HttpStatus httpStatus) {
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
