// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.azure;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.resourcemanager.eventhubs.EventHubsManager;
import com.azure.resourcemanager.eventhubs.models.EventHub;
import com.azure.resourcemanager.eventhubs.models.EventHubAuthorizationKey;
import com.azure.resourcemanager.eventhubs.models.EventHubAuthorizationRule;
import com.azure.resourcemanager.eventhubs.models.EventHubAuthorizationRules;
import com.azure.resourcemanager.eventhubs.models.EventHubs;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AzureEventHubsProvisionerTest {

  @Test
  void provisionAppliesConfiguredRetentionOnlyToDltEventHub() {
    var manager = mock(EventHubsManager.class);
    var eventHubs = mock(EventHubs.class);
    when(manager.eventHubs()).thenReturn(eventHubs);
    var mainBlank = mock(EventHub.DefinitionStages.Blank.class);
    var mainNamespace = mock(EventHub.DefinitionStages.WithCaptureProviderOrCreate.class);
    var mainCreate = mock(EventHub.DefinitionStages.WithCreate.class);
    var dltBlank = mock(EventHub.DefinitionStages.Blank.class);
    var dltNamespace = mock(EventHub.DefinitionStages.WithCaptureProviderOrCreate.class);
    var dltCreate = mock(EventHub.DefinitionStages.WithCreate.class);
    var authorizationRules = mock(EventHubAuthorizationRules.class);
    var ruleBlank = mock(EventHubAuthorizationRule.DefinitionStages.Blank.class);
    var ruleEventHub = mock(EventHubAuthorizationRule.DefinitionStages.WithAccessPolicy.class);
    var ruleCreate = mock(EventHubAuthorizationRule.DefinitionStages.WithCreate.class);
    var rule = mock(EventHubAuthorizationRule.class);
    var accessKeys = mock(EventHubAuthorizationKey.class);
    var secretStore = mock(SecretStore.class);
    var retention = Duration.ofDays(3);
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440147");
    var topicName = "opencop.byod.test-source.%s".formatted(sourceId);
    when(eventHubs.define(topicName)).thenReturn(mainBlank);
    when(mainBlank.withExistingNamespace("rg", "ns")).thenReturn(mainNamespace);
    when(mainNamespace.withPartitionCount(4)).thenReturn(mainCreate);
    when(mainCreate.create()).thenReturn(mock(EventHub.class));
    when(eventHubs.define(topicName + DLT_SUFFIX)).thenReturn(dltBlank);
    when(dltBlank.withExistingNamespace("rg", "ns")).thenReturn(dltNamespace);
    when(dltNamespace.withPartitionCount(1)).thenReturn(dltCreate);
    when(dltCreate.withRetentionPeriodInDays(3)).thenReturn(dltCreate);
    when(dltCreate.create()).thenReturn(mock(EventHub.class));
    when(eventHubs.authorizationRules()).thenReturn(authorizationRules);
    when(authorizationRules.define("byod-test-source-%s".formatted(sourceId)))
        .thenReturn(ruleBlank);
    when(ruleBlank.withExistingEventHub("rg", "ns", topicName)).thenReturn(ruleEventHub);
    when(ruleEventHub.withSendAccess()).thenReturn(ruleCreate);
    when(ruleCreate.create()).thenReturn(rule);
    when(rule.getKeys()).thenReturn(accessKeys);
    when(accessKeys.primaryConnectionString())
        .thenReturn("Endpoint=sb://ns.servicebus.windows.net/");
    var provisioner =
        new AzureEventHubsProvisioner(
            manager, "rg", "ns", "ns.servicebus.windows.net:9093", secretStore, 4, retention);

    provisioner.provision(sourceId, "Test Source", null);

    verify(dltCreate).withRetentionPeriodInDays(3);
    verify(mainCreate, never()).withRetentionPeriodInDays(org.mockito.ArgumentMatchers.anyLong());
    verify(secretStore).patch(org.mockito.ArgumentMatchers.anyString(), anyMap(), isNull());
  }
}
