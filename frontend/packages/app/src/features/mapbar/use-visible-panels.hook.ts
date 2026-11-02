// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { useBackendFeatureEnabled } from "~/features/admin/runtime-features/use-feature-flag.hook";
import { panels, type PanelDefinition } from "./mapbar.config";

export function useVisiblePanels(): PanelDefinition[] {
    const watchboxEnabled = useBackendFeatureEnabled("watchbox");

    return useMemo(
        () => panels.filter((panel) => panel.id !== "watchbox" || watchboxEnabled),
        [watchboxEnabled],
    );
}
