// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;
import org.junit.jupiter.api.Test;

class WatchboxDevStatusOverrideServiceTest {

  @Test
  void applyOverridesOnlyStatusFieldsAndPreservesEveryOtherResponseField() {
    var service = new WatchboxDevStatusOverrideService();
    var watchboxId = UUID.randomUUID();
    var layerIds = Set.of(UUID.randomUUID());
    var polygon = new Polygon();
    var filter = WatchboxFilter.empty();
    var createdAt = Instant.parse("2026-06-26T01:00:00Z");
    var updatedAt = Instant.parse("2026-06-26T02:00:00Z");
    service.set(
        watchboxId,
        new WatchboxStatusOverrideRequest(
            WatchboxAlertingStatus.FAILED,
            Set.of(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN),
            List.of()));
    var response =
        new WatchboxResponse(
            watchboxId,
            "watch",
            "description",
            true,
            WatchboxDirection.BOTH,
            polygon,
            filter,
            layerIds,
            7L,
            WatchboxAlertingStatus.ACTIVE,
            Set.of(),
            List.of(),
            // Asymmetric on purpose: pins that apply() passes the caller's capabilities through
            // by reference (a hardcoded or transposed value cannot survive this fixture).
            new WatchboxCapabilities(true, false),
            createdAt,
            updatedAt);

    var overridden = service.apply(response, false);

    assertThat(overridden)
        .isEqualTo(
            new WatchboxResponse(
                watchboxId,
                "watch",
                "description",
                true,
                WatchboxDirection.BOTH,
                polygon,
                filter,
                layerIds,
                7L,
                WatchboxAlertingStatus.FAILED,
                Set.of(WatchboxStatusReason.WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN),
                List.of(),
                new WatchboxCapabilities(true, false),
                createdAt,
                updatedAt));
  }
}
