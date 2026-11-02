// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook, act } from "~/test";
import { describe, expect, it } from "vitest";
import { useViewMode } from "./use-view-mode.hook";

describe("useViewMode", () => {
    it("defaults to view mode", () => {
        const { result } = renderHook(() => useViewMode());
        expect(result.current.mode).toBe("view");
    });

    it("toEdit switches to edit and toView switches back", () => {
        const { result } = renderHook(() => useViewMode());

        act(() => result.current.toEdit());
        expect(result.current.mode).toBe("edit");

        act(() => result.current.toView());
        expect(result.current.mode).toBe("view");
    });

    it("keeps toView/toEdit identities stable across renders", () => {
        const { result, rerender } = renderHook(() => useViewMode());
        const { toView, toEdit } = result.current;

        rerender();

        expect(result.current.toView).toBe(toView);
        expect(result.current.toEdit).toBe(toEdit);
    });
});
