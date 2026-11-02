// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { chunk } from "lodash";

/** How many per-item requests a bulk action keeps in flight at once. */
export const BULK_REQUEST_BATCH_SIZE = 8;

/**
 * Runs `run` over `items`, at most `batchSize` at a time, and returns one settled outcome per
 * item in input order. One failure never stops the rest, and a `run` that throws synchronously
 * becomes a failed outcome.
 */
export async function settleInBatches<T, R>(
    items: readonly T[],
    batchSize: number,
    run: (item: T) => Promise<R>,
): Promise<PromiseSettledResult<R>[]> {
    const outcomes: PromiseSettledResult<R>[] = [];
    for (const batch of chunk(items, batchSize)) {
        // `async` so a `run` that throws synchronously becomes a failed outcome, not a throw here.
        outcomes.push(...(await Promise.allSettled(batch.map(async (item) => run(item)))));
    }
    return outcomes;
}
