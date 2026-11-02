// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

/** One stored mapper version together with the identity it belongs to. */
record MapperCatalogRow(MapperCatalogEntryEntity mapper, MapperIdentityEntity identity) {}
