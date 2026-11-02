// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, AccessorFunction } from "@deck.gl/core";

/**
 * Safely evaluate an accessor. Returns the accessor itself if it is constant.
 *
 * @param accessor The deck.gl accessor.
 * @param object The source object.
 * @param objectInfo The object info object.
 * @returns The result of the {@link accessor}.
 */
export function access<DataT, T>(
    accessor: Accessor<DataT, T>,
    object: DataT,
    objectInfo: {
        index: number;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        data: any;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        target: any[];
    },
) {
    return typeof accessor === "function"
        ? (accessor as AccessorFunction<DataT, T>)(object, objectInfo)
        : accessor;
}
