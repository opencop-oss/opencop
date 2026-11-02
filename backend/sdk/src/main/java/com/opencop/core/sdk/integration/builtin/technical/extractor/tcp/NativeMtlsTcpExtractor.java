// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.tcp;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.policy.tcp.NativeTcpConnectionInputPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.technical.gateway.extractor.CertificateStoreOpening;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Native TCP extractor for mutual TLS with certificate stores uploaded by hand: a client store and,
 * optionally, a truststore. Each store must open with its password before it becomes material, so a
 * corrupt file or a wrong password is rejected before anything is stored.
 */
public final class NativeMtlsTcpExtractor implements TcpConnectionExtractorCapability {

  public static final UUID EXTRACTOR_ID = UUID.fromString("b7d43e19-6c2a-4f85-9e1d-3a5f8c0b2d64");

  private static final CertificateStoreFormat STORE_FORMAT = CertificateStoreFormat.PKCS12;
  private static final String COPYING_FILE_FAILED = "COPYING_FILE_FAILED";
  private static final String COPYING_FILE_FAILED_MESSAGE = "An uploaded file could not be read";

  private final NativeConnectionSchema schema;

  public NativeMtlsTcpExtractor(NativeConnectionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  @Override
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  @Override
  public UUID id() {
    return EXTRACTOR_ID;
  }

  /**
   * Builds the declaration on call rather than holding it in a constant, so a malformed field
   * reaches the registry and is rejected with the integration and extractor named.
   */
  @Override
  public ExtractorDescriptor descriptor() {
    return new ExtractorDescriptor(
        EXTRACTOR_ID,
        "TCP with mutual TLS",
        List.of(
            StandardExtractorField.HOST.declare(true),
            StandardExtractorField.PORT.declare(true),
            StandardExtractorField.CLIENT_CERTIFICATE_STORE.declare(true),
            StandardExtractorField.CLIENT_CERTIFICATE_PASSWORD.declare(true),
            StandardExtractorField.TRUSTSTORE.declare(false),
            StandardExtractorField.TRUSTSTORE_PASSWORD.declare(false)));
  }

  @Override
  public void validate(TcpConnectionInput input) throws ConnectionInputException {
    NativeTcpConnectionInputPolicy.requireEndpoint(
        StandardExtractorField.HOST.valueIn(input).orElse(null),
        StandardExtractorField.PORT.valueIn(input).orElse(null));
    NativeTcpConnectionInputPolicy.requireCertificateUploads(
        input, clientPassword(input), truststorePassword(input).orElse(null));
  }

  @Override
  public TcpConnectionMaterial extract(TcpConnectionInput input) throws ConnectionInputException {
    validate(input);
    var endpoint =
        NativeTcpConnectionInputPolicy.requireEndpoint(
            StandardExtractorField.HOST.valueIn(input).orElse(null),
            StandardExtractorField.PORT.valueIn(input).orElse(null));
    var client =
        certificate(
            StandardExtractorField.CLIENT_CERTIFICATE_STORE,
            StandardExtractorField.CLIENT_CERTIFICATE_STORE.resourcesIn(input).getFirst(),
            clientPassword(input));
    var truststore = StandardExtractorField.TRUSTSTORE.resourcesIn(input);
    Optional<CertificateMaterial> ca = Optional.empty();
    if (!truststore.isEmpty()) {
      ca =
          Optional.of(
              certificate(
                  StandardExtractorField.TRUSTSTORE,
                  truststore.getFirst(),
                  truststorePassword(input).orElseThrow()));
    }
    return TcpConnectionMaterial.mtls(endpoint, new MtlsCredentialMaterial(client, ca));
  }

  private static String clientPassword(ConnectionInput input) {
    return StandardExtractorField.CLIENT_CERTIFICATE_PASSWORD.valueIn(input).orElse(null);
  }

  private static Optional<String> truststorePassword(ConnectionInput input) {
    return StandardExtractorField.TRUSTSTORE_PASSWORD
        .valueIn(input)
        .filter(supplied -> !supplied.isBlank());
  }

  private static CertificateMaterial certificate(
      StandardExtractorField field, ConnectionInputResource resource, String password)
      throws ConnectionInputException {
    byte[] content;
    try (var stream = resource.openContent()) {
      content = CertificateStoreOpening.readBounded(field.fieldName(), stream);
    } catch (IOException exception) {
      throw new ConnectionInputException(
          ConnectionInputFailure.RESOURCE_ACCESS_FAILED,
          COPYING_FILE_FAILED,
          COPYING_FILE_FAILED_MESSAGE,
          exception);
    }
    CertificateStoreOpening.requireOpenable(field.fieldName(), content, password, STORE_FORMAT);
    return new CertificateMaterial(new DetachedBinaryMaterial(content), password, STORE_FORMAT);
  }
}
