// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Declares every integration capability a host explicitly supports or rejects without selecting a
 * provider identity. Supported prerequisites become effective support during startup.
 *
 * <p>Place host declarations on initial application configuration or ordinary imports so they are
 * available before deferred-selector processing. Declarations introduced by another {@link
 * org.springframework.context.annotation.DeferredImportSelector} or by auto-configuration are not
 * supported. Plugins contribute indexed configuration and do not use this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(IntegrationCapabilityImportSelector.class)
public @interface EnableIntegrationCapabilities {

  IntegrationCapability[] supported();

  IntegrationCapability[] rejected();
}
