// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

/**
 * The mapping choices one integration schema permits for one transport protocol.
 *
 * <p>Sealed by the shape of the choice: a framed stream pairs a mapper with a framing, any other
 * protocol chooses a mapper alone, so a framing rule on a protocol that does not frame is
 * unrepresentable rather than ignored.
 */
public sealed interface ConnectionMappingDeclaration
    permits MapperFramingDeclaration, MapperDeclaration {}
