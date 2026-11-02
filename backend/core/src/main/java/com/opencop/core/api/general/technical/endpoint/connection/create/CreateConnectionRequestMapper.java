// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Translates a v2 creation request into the application's creation, once, at the v2 entry. */
final class CreateConnectionRequestMapper {

  private CreateConnectionRequestMapper() {}

  static ConnectionCreation toCreation(CreateConnectionRequest request, Caller requestedBy) {
    return toCreation(request, List.of(), requestedBy);
  }

  /**
   * @param files the files the request uploaded, each named after the field it supplies
   */
  static ConnectionCreation toCreation(
      CreateConnectionRequest request, List<ConnectionInputResource> files, Caller requestedBy) {
    return switch (request) {
      case CreateConnectionRequest.Tcp tcp ->
          new ConnectionCreation.Tcp(
              details(tcp, tcp.mapperId(), files, requestedBy),
              framing(tcp.framing()),
              tcp.egressMapperId());
      case CreateConnectionRequest.Udp udp ->
          new ConnectionCreation.Udp(details(udp, udp.mapperId(), files, requestedBy));
      case CreateConnectionRequest.Rtsp rtsp ->
          new ConnectionCreation.Rtsp(details(rtsp, rtsp.mapperId(), files, requestedBy));
      case CreateConnectionRequest.Kafka kafka ->
          new ConnectionCreation.Kafka(details(kafka, kafka.mapperId(), files, requestedBy));
    };
  }

  private static ConnectionCreationDetails details(
      CreateConnectionRequest request,
      UUID ingressMapperId,
      List<ConnectionInputResource> files,
      Caller requestedBy) {
    return new ConnectionCreationDetails(
        request.name(),
        request.access(),
        integration(request.integration()),
        request.extractorId(),
        input(request.values(), files),
        ingressMapperId,
        requestedBy,
        request.settings());
  }

  /** The requested framing, or null when the request supplied none. */
  private static TcpFramingSelection framing(CreateConnectionRequest.Framing framing) {
    return switch (framing) {
      case null -> null;
      case CreateConnectionRequest.Framing.Custom custom ->
          new TcpFramingSelection.Custom(custom.mode(), custom.delimiter());
      case CreateConnectionRequest.Framing.Integration integration ->
          new TcpFramingSelection.Integration(integration.framingId());
    };
  }

  /**
   * The selected schema version, or null when the request named none or named it incompletely; the
   * creation rejects a missing selection with the field it concerns.
   */
  private static IntegrationConnectionSchemaReference integration(
      CreateConnectionRequest.IntegrationSelection selection) {
    if (selection == null || selection.id() == null || !isVersion(selection.schemaVersion())) {
      return null;
    }
    return new IntegrationConnectionSchemaReference(selection.id(), selection.schemaVersion());
  }

  private static boolean isVersion(Integer schemaVersion) {
    return schemaVersion != null && schemaVersion > 0;
  }

  /**
   * The supplied values and files, once every one is named and no name is used for both: a field is
   * either a value or a file, so a name supplied as both is ambiguous.
   */
  private static DeclaredConnectionInput input(
      Map<String, String> values, List<ConnectionInputResource> files) {
    var supplied = values(values);
    // A value sent as null supplies nothing, yet still claims its name.
    var valueNames = values == null ? Set.<String>of() : values.keySet();
    var conflict =
        files.stream().map(ConnectionInputResource::role).filter(valueNames::contains).findFirst();
    if (conflict.isPresent()) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.CONNECTION_INPUT_NAME_CONFLICT, conflict.get(), conflict.get());
    }
    return new DeclaredConnectionInput(supplied, files);
  }

  /** The supplied values, leaving out any sent as null, which supplies nothing. */
  private static List<ConnectionInputValue> values(Map<String, String> values) {
    if (values == null) {
      return List.of();
    }
    if (values.keySet().stream().anyMatch(name -> name == null || name.isBlank())) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.BLANK_CONNECTION_INPUT_NAME);
    }
    return values.entrySet().stream()
        .filter(entry -> Objects.nonNull(entry.getValue()))
        .map(entry -> new ConnectionInputValue(entry.getKey(), entry.getValue()))
        .toList();
  }
}
