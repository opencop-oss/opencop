// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.server;

public enum ServerAccessExceptionCodes {
  EMPTY_SERVER_ID("SERVER-ACCESS-EXCEPTION-1000", "Server Id is empty, but is mandatory"),
  SERVER_ALREADY_EXISTS("SERVER-ACCESS-EXCEPTION-1001", "Server already exists"),
  SERVER_DOES_NOT_EXISTS("REPOSITORY-EXCEPTION-1002", "Server does not exists"),
  FAILED_TO_UPDATE_LAYERS("REPOSITORY-EXCEPTION-1003", "Error occured while updating the layers"),
  ASSIGN_AT_LEAST_ONE_OWNER(
      "REPOSITORY-EXCEPTION-1004", "Assign at least one owner, either a user or a layer"),
  PRINCIPAL_FORMAT_ERROR(
      "REPOSITORY-EXCEPTION-1005",
      "User principal does not respect the format oidc:<issuer>:<userId>"),
  USER_NOT_DIRECT_MEMBER(
      "SERVER-ACCESS-EXCEPTION-1006",
      "User does not have direct access to this server (may or may not have indirect access via a layer)");

  private final String code;
  private final String description;

  ServerAccessExceptionCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String description() {
    return this.description;
  }
}
