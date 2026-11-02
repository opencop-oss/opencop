// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * A plugin's i18next resources, as one namespace it owns outright.
 *
 * A namespace rather than keys merged into the host's `translation` bundle: the
 * plugin then cannot collide with host keys or with another plugin's, and unloading
 * it removes exactly its own strings. Consumers reach them the normal way —
 * `t("<namespace>:some.key")`.
 *
 * `resources` is keyed by language code and holds the whole bundle for each, which
 * matches how the host loads its own: no async path during `install`, and switching
 * language needs no fetch.
 */
export interface TranslationContribution {
    readonly namespace: string;
    readonly resources: Readonly<Record<string, object>>;
}
