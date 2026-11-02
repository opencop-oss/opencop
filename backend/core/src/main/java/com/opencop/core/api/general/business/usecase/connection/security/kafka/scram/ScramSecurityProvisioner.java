// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.kafka.scram;

import com.opencop.core.api.general.business.usecase.connection.security.kafka.KafkaSecurityProvisioner;
import com.opencop.core.api.general.business.usecase.connection.security.kafka.ProvisionedKafkaSecurity;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import java.util.Optional;
import java.util.UUID;

/**
 * A SCRAM producer: provisions the connection's topic, SCRAM user, and ACL on the broker, with the
 * credentials stored in Vault.
 */
public final class ScramSecurityProvisioner
    implements KafkaSecurityProvisioner<ScramSecurityMaterial> {

  private final KafkaProvisioningService provisioning;

  public ScramSecurityProvisioner(KafkaProvisioningService provisioning) {
    this.provisioning = provisioning;
  }

  @Override
  public ProvisionedKafkaSecurity provision(
      UUID sourceId, String sourceName, ScramSecurityMaterial material) {
    var credentials = provisioning.provision(sourceId, sourceName, material.producerByteRate());
    return new ProvisionedKafkaSecurity(
        credentials,
        Optional.of(
            () ->
                provisioning.deprovision(
                    credentials.topicName(), credentials.username(), sourceId)));
  }
}
