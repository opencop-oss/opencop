// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useSyncExternalStore } from "react";
import type { FilterField } from "@opencop/ui";
import { createFilterFieldRegistry } from "@opencop/contributions/host";
import { i18n } from "~/i18n/config";
import { hostContainment } from "~/features/contributions/contribution-guard";

/**
 * The app's filter-field registry: the one instance of `createFilterFieldRegistry`,
 * on the host's containment and translations.
 */
export const filterFieldRegistry = createFilterFieldRegistry({
    containment: hostContainment,
    environment: { translationExists: (key) => i18n.exists(key) },
});

/** Contribute a filter field, owned by `owner`. Refuses a malformed field or a taken id. */
export const registerFilterField = filterFieldRegistry.register;

/** The contributed fields, contained, as they are now. */
export function getRegisteredFilterFields(): readonly FilterField[] {
    return filterFieldRegistry.getEntries().map((entry) => entry.field);
}

/** The contributed fields, contained, re-rendering when one is added or removed. */
export function useRegisteredFilterFields(): readonly FilterField[] {
    const entries = useSyncExternalStore(
        filterFieldRegistry.subscribe,
        filterFieldRegistry.getEntries,
    );
    return useMemo(() => entries.map((entry) => entry.field), [entries]);
}
