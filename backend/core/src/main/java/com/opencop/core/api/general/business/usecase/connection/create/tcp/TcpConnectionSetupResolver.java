// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create.tcp;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.policy.connection.TcpMapperFramingPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.policy.connection.access.ConnectionAccessPolicy;
import com.opencop.core.api.general.business.policy.connection.tcp.TcpCustomFramingPolicy;
import com.opencop.core.api.general.business.usecase.connection.create.ConnectionExtractorInvocation;
import com.opencop.core.api.general.business.usecase.connection.create.CreationExtractors;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.TcpMaterialPolicies;
import com.opencop.core.api.general.ports.gateway.connection.certificate.CertificateStoreGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpConnectionSetup;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves everything a new TCP connection selects into one validated setup, without creating or
 * storing anything, whichever integration the connection binds to.
 *
 * <p>Nothing is chosen on the requester's behalf: the extractor is the one the request names, never
 * one guessed from the input's shape, and a mapper and framing must be sent even when the
 * integration permits only one pair.
 *
 * <p>Checks run in the order that reports the most fundamental problem first: whether the transport
 * allows the access mode, whether the mode's mappers were selected, whether the integration can be
 * selected and declares TCP, whether the framing is usable, whether the integration permits the
 * ingress mapper with that framing and the mapper can be selected, whether the integration lets the
 * connection write with the egress mapper and that mapper is available, whether the extractor
 * accepts the input, and finally whether every certificate store it produced opens with its
 * password.
 */
@Component
public class TcpConnectionSetupResolver {

  static final String UNREADABLE_CERTIFICATE_STORE = "UNREADABLE_CERTIFICATE_STORE";
  private static final String UNREADABLE_CLIENT_STORE_MESSAGE =
      "The client certificate store does not open with its password";
  private static final String UNREADABLE_TRUSTSTORE_MESSAGE =
      "The truststore does not open with its password";

  private final ConnectionIntegrationSelection integrationSelection;
  private final ConnectionMappingRegistry mappingRegistry;
  private final ConnectionExtractorRegistry extractors;
  private final ConnectionMapperSelection mapperSelection;
  private final CertificateStoreGateway certificateStores;

  public TcpConnectionSetupResolver(
      ConnectionIntegrationSelection integrationSelection,
      ConnectionMappingRegistry mappingRegistry,
      ConnectionExtractorRegistry extractors,
      ConnectionMapperSelection mapperSelection,
      CertificateStoreGateway certificateStores) {
    this.integrationSelection = integrationSelection;
    this.mappingRegistry = mappingRegistry;
    this.extractors = extractors;
    this.mapperSelection = mapperSelection;
    this.certificateStores = certificateStores;
  }

  /**
   * @param mode the access mode the creation already parsed from its request
   */
  public Resolution resolve(ConnectionCreation.Tcp creation, AccessMode mode) {
    var details = creation.details();
    var integration = details.integration();
    AllowedAccessModesPolicy.assertTransportAllows(TransportProtocol.TCP, mode);
    var access =
        ConnectionAccessPolicy.resolveSelection(
            mode, details.ingressMapperId(), creation.egressMapperId());
    integrationSelection.requireSelectable(integration);
    var declaration =
        ConnectionIntegrationReferencePolicy.requireDeclaration(
            mappingRegistry.findTcp(integration), integration, TransportProtocol.TCP.name());
    var framing = framing(creation.framing());
    TcpMapperFramingPolicy.assertSelection(
        declaration, access.ingressMapperId(), Optional.of(framing));
    var ingress =
        access.ingressMapperId().map(id -> mapperSelection.ingress(declaration, id, framing));
    var egress = access.egressMapperId().map(id -> mapperSelection.egress(declaration, id));
    var extractor = CreationExtractors.require(extractors, details, ExtractorProtocol.TCP);
    var material =
        ConnectionExtractorInvocation.extract(
            integration,
            extractor,
            TcpConnectionInput.declared(details.input()),
            TcpMaterialPolicies.MATERIAL);
    requireOpenableStores(material);
    return new Resolution(
        access,
        new ResolvedTcpConnectionSetup(
            integration, extractor.id(), material, mode, ingress, egress, framing),
        extractor.descriptor());
  }

  /**
   * Requires every store the extractor produced to open with its password, whichever integration
   * produced it, so a corrupt store or a wrong password is the requester's to fix now rather than a
   * connection that fails at its first handshake. Extractors that read uploaded stores reject an
   * unreadable one themselves, naming its field; this catches the rest, such as stores taken from a
   * credential package, whose material no longer says which field they came from, so the rejection
   * names the store's role instead.
   */
  private void requireOpenableStores(TcpConnectionMaterial material) {
    if (material.security() instanceof MtlsCredentialMaterial credentials) {
      requireOpens(credentials.clientCertificate(), UNREADABLE_CLIENT_STORE_MESSAGE);
      credentials.caCertificate().ifPresent(ca -> requireOpens(ca, UNREADABLE_TRUSTSTORE_MESSAGE));
    }
  }

  private void requireOpens(CertificateMaterial certificate, String message) {
    if (!certificateStores.opensWithItsPassword(certificate)) {
      throw ConnectionManagementException.inputRejected(UNREADABLE_CERTIFICATE_STORE, message);
    }
  }

  /**
   * The framing the request selected, once its selection is complete and names a usable framing.
   */
  private ResolvedTcpFraming framing(TcpFramingSelection selection) {
    return switch (selection) {
      case null -> throw requiredField(TcpFramingSelection.FIELD);
      case TcpFramingSelection.Custom custom -> TcpCustomFramingPolicy.require(custom);
      case TcpFramingSelection.Integration integration -> {
        var framingId = integration.framingId();
        if (framingId == null) {
          throw requiredField(TcpFramingSelection.Integration.FRAMING_ID_FIELD);
        }
        if (mappingRegistry.findFraming(framingId).isEmpty()) {
          throw ConnectionManagementException.forField(
              ConnectionExceptionCodes.FRAMING_NOT_FOUND,
              TcpFramingSelection.Integration.FRAMING_ID_FIELD);
        }
        yield new ResolvedTcpFraming.Integration(framingId);
      }
    };
  }

  /**
   * What a creation selected, once valid.
   *
   * @param access the access mode and the mappers it reads and writes with, which the connection
   *     stores
   * @param setup everything the integration resolved, with the mapper revisions as provenance
   * @param extractor the descriptor of the extractor that read the input
   */
  public record Resolution(
      ConnectionAccess access, ResolvedTcpConnectionSetup setup, ExtractorDescriptor extractor) {}

  private static ConnectionManagementException requiredField(String field) {
    return ConnectionManagementException.forField(
        ConnectionExceptionCodes.REQUIRED_FIELD_INVALID, field, field);
  }
}
