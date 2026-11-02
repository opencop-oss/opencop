// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.kafka;

import com.opencop.core.api.general.business.usecase.connection.security.kafka.scram.ScramSecurityProvisioner;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import java.util.UUID;

/**
 * The provisioner of each kind of Kafka security material. A new kind of material fails to compile
 * here until it has one.
 */
public final class KafkaSecurityProvisionerRegistry {

  private final ScramSecurityProvisioner scram;

  KafkaSecurityProvisionerRegistry(ScramSecurityProvisioner scram) {
    this.scram = scram;
  }

  public static KafkaSecurityProvisionerRegistry standard(KafkaProvisioningService provisioning) {
    return new KafkaSecurityProvisionerRegistry(new ScramSecurityProvisioner(provisioning));
  }

  public ProvisionedKafkaSecurity provision(
      UUID sourceId, String sourceName, KafkaSecurityMaterial material) {
    return switch (material) {
      case ScramSecurityMaterial credentials -> scram.provision(sourceId, sourceName, credentials);
    };
  }
}
