// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PartialOCFeature } from "@opencop/elements";

/**
 * Stamps the timestamps a broadcast create expects: `time` is reset to `now`, and a missing
 * `start` falls back to `now`. Everything else on the feature, `stale` included, is kept.
 *
 * Meant for any create built from an existing feature, so those creates normalize the same way.
 * The backend re-mints id, objectId and time itself, so in practice only `start` matters.
 */
export function stampBroadcastTimestamps(feature: PartialOCFeature, now: number): PartialOCFeature {
    const { opencop } = feature.properties;
    return {
        ...feature,
        properties: {
            opencop: {
                ...opencop,
                timestampz: {
                    ...opencop.timestampz,
                    start: opencop.timestampz.start ?? now,
                    time: now,
                },
            },
        },
    };
}
