// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.tcp;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a native TCP connection's input must hold: a usable endpoint and, for mutual TLS, exactly
 * the certificate uploads the connection needs. Each rejection names the field it concerns.
 */
public final class NativeTcpConnectionInputPolicy {

  public static final int MIN_PORT = 1;
  public static final int MAX_PORT = 65_535;

  public static final String MISSING_HOST = "MISSING_TCP_HOST";
  public static final String MISSING_PORT = "MISSING_TCP_PORT";
  public static final String INVALID_PORT = "INVALID_TCP_PORT";
  public static final String UNEXPECTED_FILE = "UNEXPECTED_TCP_FILE";
  public static final String MISSING_CERTIFICATE_STORE = "MISSING_CERTIFICATE_STORE";
  public static final String DUPLICATE_CERTIFICATE_STORE = "DUPLICATE_CERTIFICATE_STORE";
  public static final String EMPTY_CERTIFICATE_STORE = "EMPTY_CERTIFICATE_STORE";
  public static final String MISSING_CERTIFICATE_PASSWORD = "MISSING_CERTIFICATE_PASSWORD";
  public static final String UNEXPECTED_CERTIFICATE_PASSWORD = "UNEXPECTED_CERTIFICATE_PASSWORD";

  private static final String MISSING_HOST_MESSAGE = "A TCP connection requires a host";
  private static final String MISSING_PORT_MESSAGE = "A TCP connection requires a port";
  private static final String INVALID_PORT_MESSAGE =
      "Port must be a whole number between " + MIN_PORT + " and " + MAX_PORT;
  private static final String UNEXPECTED_FILE_MESSAGE =
      "This connection accepts no file named '%s'";
  private static final String MISSING_CERTIFICATE_STORE_MESSAGE =
      "Mutual TLS requires a client certificate store";
  private static final String DUPLICATE_CERTIFICATE_STORE_MESSAGE =
      "Only one file may be supplied as '%s'";
  private static final String EMPTY_CERTIFICATE_STORE_MESSAGE =
      "The file supplied as '%s' is empty";
  private static final String MISSING_CERTIFICATE_PASSWORD_MESSAGE =
      "The certificate store supplied as '%s' requires its password";
  private static final String UNEXPECTED_CERTIFICATE_PASSWORD_MESSAGE =
      "A truststore password was supplied without a truststore";

  private static final Set<String> CERTIFICATE_FIELDS =
      Set.of(
          StandardExtractorField.CLIENT_CERTIFICATE_STORE.fieldName(),
          StandardExtractorField.TRUSTSTORE.fieldName());

  private NativeTcpConnectionInputPolicy() {}

  /** The endpoint a request supplied as text, once both parts are usable. */
  public static TcpEndpointMaterial requireEndpoint(String host, String suppliedPort)
      throws ConnectionInputException {
    var usableHost = requireHost(host);
    if (suppliedPort == null || suppliedPort.isBlank()) {
      throw portRejection(MISSING_PORT, MISSING_PORT_MESSAGE);
    }
    try {
      return new TcpEndpointMaterial(
          usableHost, requirePort(Integer.parseInt(suppliedPort.strip())));
    } catch (NumberFormatException _) {
      throw portRejection(INVALID_PORT, INVALID_PORT_MESSAGE);
    }
  }

  /** The endpoint a request supplied typed, once both parts are usable. */
  public static TcpEndpointMaterial requireEndpoint(String host, Integer port)
      throws ConnectionInputException {
    var usableHost = requireHost(host);
    if (port == null) {
      throw portRejection(MISSING_PORT, MISSING_PORT_MESSAGE);
    }
    return new TcpEndpointMaterial(usableHost, requirePort(port));
  }

  /** Rejects any upload, for a connection that carries no certificates. */
  public static void requireNoFiles(ConnectionInput input) throws ConnectionInputException {
    if (!input.resources().isEmpty()) {
      throw unexpectedFile(input.resources().getFirst());
    }
  }

  /**
   * Requires exactly the uploads mutual TLS needs: one non-empty client certificate store with its
   * password, and at most one non-empty truststore, which comes with its password.
   */
  public static void requireCertificateUploads(
      ConnectionInput input, String clientPassword, String truststorePassword)
      throws ConnectionInputException {
    var foreign =
        input.resources().stream()
            .filter(resource -> !CERTIFICATE_FIELDS.contains(resource.role()))
            .findFirst();
    if (foreign.isPresent()) {
      throw unexpectedFile(foreign.get());
    }
    var client = StandardExtractorField.CLIENT_CERTIFICATE_STORE.resourcesIn(input);
    if (client.isEmpty()) {
      throw rejection(
          StandardExtractorField.CLIENT_CERTIFICATE_STORE,
          MISSING_CERTIFICATE_STORE,
          MISSING_CERTIFICATE_STORE_MESSAGE);
    }
    requireOneNonEmpty(StandardExtractorField.CLIENT_CERTIFICATE_STORE, client);
    requirePassword(
        StandardExtractorField.CLIENT_CERTIFICATE_PASSWORD,
        StandardExtractorField.CLIENT_CERTIFICATE_STORE,
        clientPassword);

    var truststore = StandardExtractorField.TRUSTSTORE.resourcesIn(input);
    if (truststore.isEmpty()) {
      if (isPresent(truststorePassword)) {
        throw rejection(
            StandardExtractorField.TRUSTSTORE_PASSWORD,
            UNEXPECTED_CERTIFICATE_PASSWORD,
            UNEXPECTED_CERTIFICATE_PASSWORD_MESSAGE);
      }
      return;
    }
    requireOneNonEmpty(StandardExtractorField.TRUSTSTORE, truststore);
    requirePassword(
        StandardExtractorField.TRUSTSTORE_PASSWORD,
        StandardExtractorField.TRUSTSTORE,
        truststorePassword);
  }

  private static String requireHost(String host) throws ConnectionInputException {
    return Optional.ofNullable(host)
        .filter(NativeTcpConnectionInputPolicy::isPresent)
        .orElseThrow(
            () -> rejection(StandardExtractorField.HOST, MISSING_HOST, MISSING_HOST_MESSAGE));
  }

  private static int requirePort(int port) throws ConnectionInputException {
    if (port < MIN_PORT || port > MAX_PORT) {
      throw portRejection(INVALID_PORT, INVALID_PORT_MESSAGE);
    }
    return port;
  }

  private static void requireOneNonEmpty(
      StandardExtractorField field, List<ConnectionInputResource> uploads)
      throws ConnectionInputException {
    if (uploads.size() > 1) {
      throw rejection(
          field,
          DUPLICATE_CERTIFICATE_STORE,
          DUPLICATE_CERTIFICATE_STORE_MESSAGE.formatted(field.fieldName()));
    }
    if (uploads.getFirst().byteSize() == 0) {
      throw rejection(
          field,
          EMPTY_CERTIFICATE_STORE,
          EMPTY_CERTIFICATE_STORE_MESSAGE.formatted(field.fieldName()));
    }
  }

  private static void requirePassword(
      StandardExtractorField passwordField, StandardExtractorField storeField, String password)
      throws ConnectionInputException {
    if (!isPresent(password)) {
      throw rejection(
          passwordField,
          MISSING_CERTIFICATE_PASSWORD,
          MISSING_CERTIFICATE_PASSWORD_MESSAGE.formatted(storeField.fieldName()));
    }
  }

  private static boolean isPresent(String supplied) {
    return supplied != null && !supplied.isBlank();
  }

  private static ConnectionInputException unexpectedFile(ConnectionInputResource resource) {
    return ConnectionInputException.rejecting(
        resource.role(),
        ConnectionInputFailure.INVALID_INPUT,
        UNEXPECTED_FILE,
        UNEXPECTED_FILE_MESSAGE.formatted(resource.role()));
  }

  private static ConnectionInputException portRejection(String detailCode, String message) {
    return rejection(StandardExtractorField.PORT, detailCode, message);
  }

  private static ConnectionInputException rejection(
      StandardExtractorField field, String detailCode, String message) {
    return ConnectionInputException.rejecting(
        field.fieldName(), ConnectionInputFailure.INVALID_INPUT, detailCode, message);
  }
}
