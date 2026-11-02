// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Discriminated drawer state: `closed`, or an open drawer of kind `K` carrying its `entity`.
 * `K` has no default, so an omitted discriminant is a compile error rather than a silent
 * widening to `string` that would drop exhaustive narrowing.
 */
export type DrawerState<T, K extends string> = { type: "closed" } | { type: K; entity: T };
