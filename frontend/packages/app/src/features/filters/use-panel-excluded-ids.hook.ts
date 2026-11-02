// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { useFilterPanelStore } from "./filter-panel.store";

/** The ids the user removed from the map panel, as a set for cheap membership checks. */
export function usePanelExcludedIds(): ReadonlySet<string> {
    const excludedIds = useFilterPanelStore((s) => s.excludedIds);
    return useMemo(() => new Set(excludedIds), [excludedIds]);
}
