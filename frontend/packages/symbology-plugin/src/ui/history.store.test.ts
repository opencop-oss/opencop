// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
// Plain RTL renderHook, not the plugin's test harness: these are pure zustand
// selectors with no React context dependency, so no provider wrapper is needed.
import { renderHook } from "@testing-library/react";
import { SIDC } from "@opencop/symbology";
import { useHistory } from "./history.store";
import { useSymbolHistoryStore } from "./symbol-history.store";

const SIDC_A = SIDC.default();
const SIDC_B = SIDC.fromString("10061000141211000000");

beforeEach(() => {
    useSymbolHistoryStore.setState({ favorites: [], recents: [] });
});

describe("addFavorite", () => {
    it("appends a new entry", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe(SIDC_A.toString());
    });

    it("stores the label when provided", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {}, "Alpha");
        expect(useSymbolHistoryStore.getState().favorites[0].label).toBe("Alpha");
    });

    it("ignores a duplicate SIDC", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
    });
});

describe("removeFavorite", () => {
    it("removes the entry by id", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        const { id } = useSymbolHistoryStore.getState().favorites[0];
        useSymbolHistoryStore.getState().removeFavorite(id);
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(0);
    });

    it("leaves other favorites intact", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        useSymbolHistoryStore.getState().addFavorite(SIDC_B, {});
        const { id } = useSymbolHistoryStore.getState().favorites[0];
        useSymbolHistoryStore.getState().removeFavorite(id);
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
    });
});

describe("removeFavoriteByCode", () => {
    it("removes the entry matching the SIDC string", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        useSymbolHistoryStore.getState().removeFavoriteByCode(SIDC_A.toString());
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(0);
    });
});

describe("addRecent", () => {
    it("prepends to recents", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        useSymbolHistoryStore.getState().addRecent(SIDC_B.toString(), {});
        expect(useSymbolHistoryStore.getState().recents[0].sidc).toBe(SIDC_B.toString());
    });

    it("deduplicates — moves an existing entry to the front", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        useSymbolHistoryStore.getState().addRecent(SIDC_B.toString(), {});
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        const recents = useSymbolHistoryStore.getState().recents;
        expect(recents).toHaveLength(2);
        expect(recents[0].sidc).toBe(SIDC_A.toString());
    });

    it("preserves an existing label when re-adding without one", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {}, "Alpha");
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        expect(useSymbolHistoryStore.getState().recents[0].label).toBe("Alpha");
    });

    it("accepts a SIDC object as well as a string", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A, {});
        expect(useSymbolHistoryStore.getState().recents[0].sidc).toBe(SIDC_A.toString());
    });
});

describe("promoteRecentToFavorite", () => {
    it("adds the recent to favorites", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        const { id } = useSymbolHistoryStore.getState().recents[0];
        useSymbolHistoryStore.getState().promoteRecentToFavorite(id);
        expect(useSymbolHistoryStore.getState().favorites[0].sidc).toBe(SIDC_A.toString());
    });

    it("applies an optional label override", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        const { id } = useSymbolHistoryStore.getState().recents[0];
        useSymbolHistoryStore.getState().promoteRecentToFavorite(id, "Bravo");
        expect(useSymbolHistoryStore.getState().favorites[0].label).toBe("Bravo");
    });

    it("does not add a duplicate when the SIDC is already a favorite", () => {
        useSymbolHistoryStore.getState().addFavorite(SIDC_A, {});
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        const { id } = useSymbolHistoryStore.getState().recents[0];
        useSymbolHistoryStore.getState().promoteRecentToFavorite(id);
        expect(useSymbolHistoryStore.getState().favorites).toHaveLength(1);
    });
});

describe("updateRecentLabel", () => {
    it("updates the label for the matching SIDC", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        useSymbolHistoryStore.getState().updateRecentLabel(SIDC_A.toString(), "Updated");
        expect(useSymbolHistoryStore.getState().recents[0].label).toBe("Updated");
    });

    it("does not affect other entries", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {}, "Alpha");
        useSymbolHistoryStore.getState().addRecent(SIDC_B.toString(), {}, "Bravo");
        useSymbolHistoryStore.getState().updateRecentLabel(SIDC_A.toString(), "Updated");
        const b = useSymbolHistoryStore
            .getState()
            .recents.find((r) => r.sidc === SIDC_B.toString());
        expect(b?.label).toBe("Bravo");
    });
});

describe("useHistory", () => {
    it("returns the symbol store contents when passed the symbol store", () => {
        useSymbolHistoryStore.getState().addRecent(SIDC_A.toString(), {});
        const { result } = renderHook(() => useHistory(useSymbolHistoryStore));
        expect(result.current.recents).toHaveLength(1);
    });
});
