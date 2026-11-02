// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;

/**
 * One operation a caller may invoke.
 *
 * @param path the operation's path template relative to its extension, such as {@code
 *     /subscriptions/{name}}
 */
public record ExtensionOperationSummary(ExtensionMethod method, String path, AccessLevel access) {}
