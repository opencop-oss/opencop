// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { act } from "@testing-library/react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { installShapes } from "../install-shapes";
import { useNumericInput } from "./use-numeric-input.hook";

/** Rendered in a test host with shapes installed — the environment a native's hooks run in. */
const renderHook: ReturnType<typeof createTestHost>["renderHook"] = (callback, options) =>
    createTestHost({ id: "opencop.shapes", install: installShapes }).renderHook(callback, options);

describe("useNumericInput", () => {
    it("shows the formatted live value while unfocused", () => {
        const { result } = renderHook(() => useNumericInput(42.6, vi.fn(), (v) => v.toFixed(1)));
        expect(result.current.value).toBe("42.6");
    });

    it("defaults to plain toString when no format is given", () => {
        const { result } = renderHook(() => useNumericInput(3.5, vi.fn()));
        expect(result.current.value).toBe("3.5");
    });

    it("tracks live-value changes while unfocused", () => {
        const { result, rerender } = renderHook(
            ({ value }: { value: number }) => useNumericInput(value, vi.fn(), (v) => v.toFixed(0)),
            { initialProps: { value: 10 } },
        );
        expect(result.current.value).toBe("10");
        rerender({ value: 25 });
        expect(result.current.value).toBe("25");
    });

    it("switches to a local draft on focus, seeded from the formatted value", () => {
        const { result } = renderHook(() => useNumericInput(7, vi.fn(), (v) => v.toFixed(2)));
        act(() => result.current.onFocusChange({ focused: true }));
        expect(result.current.value).toBe("7.00");
    });

    it("reflects raw typed text immediately while focused, even mid-edit", () => {
        const onChange = vi.fn();
        const { result } = renderHook(() => useNumericInput(0, onChange));
        act(() => result.current.onFocusChange({ focused: true }));
        act(() => result.current.onValueChange({ value: "-", valueAsNumber: NaN }));
        expect(result.current.value).toBe("-");
        expect(onChange).not.toHaveBeenCalled();
    });

    it("calls onChange with the parsed number for a valid non-negative entry", () => {
        const onChange = vi.fn();
        const { result } = renderHook(() => useNumericInput(0, onChange));
        act(() => result.current.onFocusChange({ focused: true }));
        act(() => result.current.onValueChange({ value: "12", valueAsNumber: 12 }));
        expect(onChange).toHaveBeenCalledWith(12);
    });

    it("rejects NaN entries without calling onChange, but keeps the typed draft on screen", () => {
        const onChange = vi.fn();
        const { result } = renderHook(() => useNumericInput(5, onChange));
        act(() => result.current.onFocusChange({ focused: true }));
        act(() => result.current.onValueChange({ value: "abc", valueAsNumber: NaN }));
        expect(onChange).not.toHaveBeenCalled();
        expect(result.current.value).toBe("abc");
    });

    it("rejects negative entries without calling onChange", () => {
        const onChange = vi.fn();
        const { result } = renderHook(() => useNumericInput(5, onChange));
        act(() => result.current.onFocusChange({ focused: true }));
        act(() => result.current.onValueChange({ value: "-3", valueAsNumber: -3 }));
        expect(onChange).not.toHaveBeenCalled();
        expect(result.current.value).toBe("-3");
    });

    it("reverts to the formatted live value on blur, discarding an invalid draft", () => {
        const { result } = renderHook(() => useNumericInput(9, vi.fn(), (v) => v.toFixed(1)));
        act(() => result.current.onFocusChange({ focused: true }));
        act(() => result.current.onValueChange({ value: "abc", valueAsNumber: NaN }));
        act(() => result.current.onFocusChange({ focused: false }));
        expect(result.current.value).toBe("9.0");
    });
});
