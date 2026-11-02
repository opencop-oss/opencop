// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka.properties;

public sealed interface ProviderConfig permits AzureProviderProperties, LocalProviderProperties {

  String name();
}
