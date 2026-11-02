// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { noop } from "lodash";
import type { ReactNode } from "react";
import { ToolSessionScope, useToolSession, useToolSessionRevision } from "./ToolSessionContext";
import type { ToolSession } from "./tool-session";

const session: ToolSession = {
    draft: null,
    params: { shape: "circle" },
    phase: "draw",
    setParams: noop,
    invalidate: noop,
    activate: noop,
    cancel: noop,
};

const inScope =
    (revision: number) =>
    ({ children }: { children: ReactNode }) => (
        <ToolSessionScope session={session} revision={revision}>
            {children}
        </ToolSessionScope>
    );

describe("ToolSessionScope", () => {
    it("gives an editor the session it was scoped to", () => {
        const { result } = renderHook(() => useToolSession(), { wrapper: inScope(3) });

        expect(result.current).toBe(session);
    });

    it("gives an editor the scope's geometry revision", () => {
        const { result } = renderHook(() => useToolSessionRevision(), { wrapper: inScope(7) });

        expect(result.current).toBe(7);
    });

    it("throws in development when an editor reads its session outside a scope", () => {
        expect(() => renderHook(() => useToolSession())).toThrow(/ToolSessionScope/);
    });
});
