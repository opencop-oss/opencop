// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { act } from "@testing-library/react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { installShapes } from "../install-shapes";
import { useUnitState } from "./use-unit-state.hook";

/** Rendered in a test host with shapes installed — the environment a native's hooks run in. */
const renderHook: ReturnType<typeof createTestHost>["renderHook"] = (callback, options) =>
    createTestHost({ id: "opencop.shapes", install: installShapes }).renderHook(callback, options);

describe("useUnitState", () => {
    it("returns the fallback when no override has been set", () => {
        const { result } = renderHook(() => useUnitState<string>("el1:metric", "m"));
        expect(result.current[0]).toBe("m");
    });

    it("keeps the override across a re-render with the same resetKey", () => {
        const { result, rerender } = renderHook(
            ({ key }: { key: string }) => useUnitState<string>(key, "m"),
            { initialProps: { key: "el1:metric" } },
        );

        act(() => result.current[1]("km"));
        expect(result.current[0]).toBe("km");

        rerender({ key: "el1:metric" });
        expect(result.current[0]).toBe("km");
    });

    it("drops the override and falls back once resetKey changes", () => {
        const { result, rerender } = renderHook(
            ({ key }: { key: string }) => useUnitState<string>(key, "m"),
            { initialProps: { key: "el1:metric" } },
        );

        act(() => result.current[1]("km"));
        rerender({ key: "el2:metric" });
        expect(result.current[0]).toBe("m");
    });

    it("uses the new fallback once resetKey changes, when no override was set", () => {
        const { result, rerender } = renderHook(
            ({ key, fallback }: { key: string; fallback: string }) =>
                useUnitState<string>(key, fallback),
            { initialProps: { key: "el1:metric", fallback: "m" } },
        );

        rerender({ key: "el2:imperial", fallback: "ft" });
        expect(result.current[0]).toBe("ft");
    });
});
