// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Provider-neutral startup capability contracts for integration provider authors.
 *
 * <p>Integration providers declare ordinary Spring components or statically typed {@code @Bean}
 * methods that implement the contribution interfaces owned by a capability. Prefer a
 * capability-focused registration class; one class may implement several interfaces owned by the
 * same capability, and a provider may contribute multiple registration classes and beans for one
 * capability. Do not hide contributions behind {@code Object}, opaque {@code FactoryBean} products,
 * XML, or indeterminate programmatic definitions.
 *
 * <p>An integration provider here is a startup-classpath contributor to this capability system. It
 * is distinct from the legacy Processor plugin API under {@code com.opencop.core.sdk.ports.plugin},
 * which uses plugin-specific annotations and contracts.
 *
 * <p>Providers add no OpenCOP activation annotations, conditions, enablement flags, manifests,
 * priorities, or overrides. Capability activation declarations and SDK activation infrastructure
 * contain no provider identity. Direct provider controllers are unsupported; future endpoint
 * contributions require an SDK-owned contract and host-controlled publisher. Installed providers
 * are trusted startup dependencies with the host's process privileges, not isolated or
 * hot-swappable code.
 */
package com.opencop.core.sdk.business.model.integration.capability;
