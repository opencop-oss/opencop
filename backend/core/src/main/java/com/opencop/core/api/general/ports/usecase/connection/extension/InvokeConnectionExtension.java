// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

/** Invokes an operation an integration's extension declares, on one connection. */
public interface InvokeConnectionExtension {

  ExtensionResult invoke(InvokeConnectionExtensionCommand command);
}
