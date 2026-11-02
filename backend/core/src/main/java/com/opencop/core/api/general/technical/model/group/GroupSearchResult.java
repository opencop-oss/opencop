// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import java.util.UUID;

/**
 * Minimal projection of a user group for {@code GET /v1/user-groups/search}, carrying only an id to
 * select by and a name to display.
 *
 * <p>The description, creation timestamp and IdP identifiers are omitted on purpose. This endpoint
 * is open to every authenticated caller, so anything it does not need stays off the wire.
 *
 * <p>{@code source} is the exception, and is needed rather than merely useful: a local group and a
 * SCIM group may legally share a name, so without it two candidates in an assign picker are
 * indistinguishable. It classifies management without disclosing the issuer itself.
 */
public record GroupSearchResult(UUID id, String name, GroupManagementSource source) {}
