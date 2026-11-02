// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.setup;

import com.opencop.core.api.general.business.factory.connection.RtspConnectionSpecFactory;
import com.opencop.core.api.general.business.factory.connection.TcpConnectionSpecFactory;
import com.opencop.core.api.general.business.factory.connection.UdpConnectionSpecFactory;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.model.connection.setup.KafkaSetup;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.policy.connection.ConnectionSettingsPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AccessModeFormatPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.usecase.connection.create.MapperOnlyCreationPreparation;
import com.opencop.core.api.general.business.usecase.connection.create.tcp.TcpConnectionSetupResolver;
import com.opencop.core.api.general.business.usecase.connection.security.rtsp.RtspSecurityProvisionerRegistry;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.TcpSecurityProvisionerRegistry;
import com.opencop.core.api.general.business.usecase.connection.security.udp.UdpSecurityProvisionerRegistry;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns a submitted connection setup into one ready to be stored for a connection.
 *
 * <p>Every selection is checked before anything is provisioned, in the order a client sees its
 * rejections in: the access mode, the integration, the extractor and the integration settings
 * first, then what the transport selects. Only then is a socket setup's security material
 * provisioned for the connection and its spec built; should storing it fail, its cleanup deletes
 * exactly what was provisioned. A Kafka setup's broker resources are provisioned as it is stored.
 *
 * <p>The setup's name and owner are not read: checking the name, authorizing the caller and storing
 * the setup are its caller's.
 */
@Component
public class ConnectionSetupResolution {

  private final TcpConnectionSetupResolver tcpResolver;
  private final MapperOnlyCreationPreparation mapperOnlyPreparation;
  private final ConnectionSettingsRegistry settingsRegistry;
  private final TcpSecurityProvisionerRegistry tcpSecurity;
  private final UdpSecurityProvisionerRegistry udpSecurity;
  private final RtspSecurityProvisionerRegistry rtspSecurity;

  public ConnectionSetupResolution(
      TcpConnectionSetupResolver tcpResolver,
      MapperOnlyCreationPreparation mapperOnlyPreparation,
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionStore connectionStore) {
    this.tcpResolver = tcpResolver;
    this.mapperOnlyPreparation = mapperOnlyPreparation;
    this.settingsRegistry = settingsRegistry;
    this.tcpSecurity = TcpSecurityProvisionerRegistry.standard(connectionStore);
    this.udpSecurity = UdpSecurityProvisionerRegistry.standard();
    this.rtspSecurity = RtspSecurityProvisionerRegistry.standard();
  }

  /**
   * Resolves a TCP setup.
   *
   * @param sourceId the connection the security material is provisioned for
   */
  public ConnectionSetup resolve(ConnectionCreation.Tcp creation, UUID sourceId) {
    var selection = select(creation.details());
    var resolution = tcpResolver.resolve(creation, selection.mode());
    var tcp = resolution.setup();
    var security = tcpSecurity.provision(sourceId, tcp.material().security());
    var spec =
        TcpConnectionSpecFactory.create(
            tcp.framing(), tcp.material().endpoint(), security.security(), security.timeouts());
    return new ConnectionSetup(
        resolution.access(),
        tcp.integration(),
        spec,
        selection.settings(),
        SetupPrefill.of(resolution.extractor(), creation.details().input()),
        security.cleanup());
  }

  /**
   * Resolves a UDP setup. A UDP connection only listens, so it only reads.
   *
   * @param sourceId the connection the security material is provisioned for
   */
  public ConnectionSetup resolve(ConnectionCreation.Udp creation, UUID sourceId) {
    var details = creation.details();
    var selection = select(details);
    AllowedAccessModesPolicy.assertTransportAllows(TransportProtocol.UDP, selection.mode());
    var prepared = mapperOnlyPreparation.prepare(details, selection.mode(), ExtractorProtocol.UDP);
    var security = udpSecurity.provision(sourceId, prepared.material().security());
    return new ConnectionSetup(
        prepared.access(),
        details.integration(),
        UdpConnectionSpecFactory.create(prepared.material().endpoint()),
        selection.settings(),
        SetupPrefill.of(prepared.extractor(), details.input()),
        security.cleanup());
  }

  /**
   * Resolves an RTSP setup. An RTSP connection consumes a stream, so it only reads.
   *
   * @param sourceId the connection the security material is provisioned for
   */
  public ConnectionSetup resolve(ConnectionCreation.Rtsp creation, UUID sourceId) {
    var details = creation.details();
    var selection = select(details);
    AllowedAccessModesPolicy.assertTransportAllows(TransportProtocol.RTSP, selection.mode());
    var prepared = mapperOnlyPreparation.prepare(details, selection.mode(), ExtractorProtocol.RTSP);
    var security = rtspSecurity.provision(sourceId, prepared.material().security());
    return new ConnectionSetup(
        prepared.access(),
        details.integration(),
        RtspConnectionSpecFactory.create(prepared.material().endpoint()),
        selection.settings(),
        SetupPrefill.of(prepared.extractor(), details.input()),
        security.cleanup());
  }

  /**
   * Resolves a Kafka setup. A Kafka connection consumes its topic, so it only reads, and it runs no
   * integration behavior, so it takes no settings.
   */
  public KafkaSetup resolve(ConnectionCreation.Kafka creation) {
    var details = creation.details();
    var selection = select(details);
    ConnectionSettingsPolicy.requireNone(selection.settings());
    AllowedAccessModesPolicy.assertTransportAllows(TransportProtocol.KAFKA, selection.mode());
    var prepared =
        mapperOnlyPreparation.prepare(details, selection.mode(), ExtractorProtocol.KAFKA);
    return new KafkaSetup(
        prepared.access(),
        details.integration(),
        SetupPrefill.of(prepared.extractor(), details.input()),
        prepared.material().security());
  }

  /** What every setup selects before its transport is looked at, once each is valid. */
  private Selection select(ConnectionCreationDetails details) {
    requireAccess(details);
    var mode = AccessModeFormatPolicy.parse(details.access());
    requireIntegration(details);
    requireExtractor(details);
    return new Selection(mode, settings(details));
  }

  private record Selection(AccessMode mode, SettingsDocument settings) {}

  private static void requireAccess(ConnectionCreationDetails details) {
    if (details.access() == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
          ConnectionCreationDetails.ACCESS_FIELD,
          ConnectionCreationDetails.ACCESS_FIELD);
    }
  }

  private static void requireIntegration(ConnectionCreationDetails details) {
    if (details.integration() == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
          ConnectionCreationDetails.INTEGRATION_FIELD,
          ConnectionCreationDetails.INTEGRATION_FIELD);
    }
  }

  private static void requireExtractor(ConnectionCreationDetails details) {
    if (details.extractorId() == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
          ConnectionCreationDetails.EXTRACTOR_FIELD,
          ConnectionCreationDetails.EXTRACTOR_FIELD);
    }
  }

  /**
   * Validates the requested overrides against the selected schema version's declared settings and
   * returns them normalised, or empty when the request sent none. Every violation is reported
   * together.
   *
   * <p>A schema version this runtime does not register is left to the transport's resolution, which
   * rejects the selection itself rather than claiming that the schema takes no settings.
   */
  private SettingsDocument settings(ConnectionCreationDetails details) {
    if (details.settings() == null || settingsRegistry.find(details.integration()).isEmpty()) {
      return SettingsDocument.empty();
    }
    return ConnectionSettingsPolicy.requireResolved(
        settingsRegistry.resolve(details.integration(), details.settings()));
  }
}
