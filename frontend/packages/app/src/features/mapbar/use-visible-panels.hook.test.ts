// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook } from "~/test";
import { describe, expect, it } from "vitest";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { useVisiblePanels } from "./use-visible-panels.hook";

function renderVisiblePanels(watchboxEnabled: boolean) {
    return renderHook(() => useVisiblePanels(), {
        queryData: {
            [JSON.stringify(appMetadataQueries.detail().queryKey)]: {
                features: [{ name: "watchbox", enabled: watchboxEnabled }],
            },
        },
    });
}

describe("useVisiblePanels", () => {
    it("includes the watchbox panel when the backend feature is enabled", () => {
        const { result } = renderVisiblePanels(true);

        expect(result.current.some((panel) => panel.id === "watchbox")).toBe(true);
    });

    it("excludes the watchbox panel when the backend feature is disabled", () => {
        const { result } = renderVisiblePanels(false);

        expect(result.current.some((panel) => panel.id === "watchbox")).toBe(false);
    });
});
