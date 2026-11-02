// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import { useFilterPanelStore } from "./filter-panel.store";
import { isOffered, shouldEnableSource } from "./panel-exclusion.util";

describe("shouldEnableSource", () => {
    beforeEach(() => {
        useFilterPanelStore.setState({ hiddenSources: [] });
        useFilterPanelStore.setState({ excludedIds: [] });
    });

    it("enables a source that is neither toggled off nor excluded", () => {
        expect(shouldEnableSource("a")).toBe(true);
    });

    it("keeps a toggled-off source disabled", () => {
        useFilterPanelStore.setState({ hiddenSources: ["a"] });
        expect(shouldEnableSource("a")).toBe(false);
    });

    it("keeps an excluded source disabled even when it is not toggled off", () => {
        useFilterPanelStore.setState({ excludedIds: ["a"] });
        expect(shouldEnableSource("a")).toBe(false);
        expect(shouldEnableSource("b")).toBe(true);
    });
});

describe("isOffered", () => {
    const excluded = new Set(["a"]);

    it("offers anything not removed from the panel", () => {
        expect(isOffered(excluded, "b")).toBe(true);
    });

    it("does not offer a removed id", () => {
        expect(isOffered(excluded, "a")).toBe(false);
    });

    it("still offers a removed id that is in the keep list", () => {
        expect(isOffered(excluded, "a", ["x", "a"])).toBe(true);
    });
});
