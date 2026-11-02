// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { createPointGrammar, createSequentialGrammar } from "./grammars";
import { resolveGrammar, type ToolDescriptor } from "./tool";

/**
 * The tool contract's own pure resolution, pinned in the package that owns it.
 * Everything else a tool gets by declaring nothing — the crosshair, the Escape
 * meanings, completing into an edit session, the element store in `produces` holding the product — is a
 * host default and is pinned where the host applies it.
 */

const bare: ToolDescriptor = { id: "t", label: "t", icon: () => null };

describe("resolveGrammar", () => {
    const create = () => ({}) as never;
    const staticGrammar = createSequentialGrammar({ create });

    it("returns a static grammar as-is", () => {
        expect(resolveGrammar({ ...bare, draw: { grammar: staticGrammar } }, {})).toBe(
            staticGrammar,
        );
    });

    it("invokes a params-derived grammar with the params", () => {
        const derived = createPointGrammar({ create });
        const descriptor: ToolDescriptor = {
            ...bare,
            draw: {
                grammar: (params) => {
                    expect(params).toEqual({ sidc: "X" });
                    return derived;
                },
            },
        };

        expect(resolveGrammar(descriptor, { sidc: "X" })).toBe(derived);
    });

    it("returns undefined for a content-only (popover) tool with no grammar", () => {
        expect(resolveGrammar({ ...bare, content: () => null }, {})).toBeUndefined();
    });
});
