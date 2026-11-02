// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMapper } from "@opencop/contributions";
import { beforeAll, describe, expect, it } from "vitest";
import { SIDC } from "@opencop/symbology";
import type { Element } from "@opencop/elements";
import {
    resolveGrammar,
    type DrawHintState,
    type ToolDescriptor,
    producedElementType,
} from "@opencop/tools";
import { installSymbology } from "./install-symbology";
import { useSymbolHistoryStore } from "./symbol-history.store";

/**
 * Symbology contributes its own tools through the shared `ContributionContext` —
 * the same path a plugin's `install(ctx)` uses. These assert the contribution
 * survives intact (grammar, editors, element-type mapping) and that the
 * `onCommit` hook replaces the old hardcoded `if (type === "symbol")` branch in
 * the generic save path.
 */
/**
 * Captures what `install` contributes, standing in for whichever context calls it —
 * the plugin manager's recorder in production. Registering against a local fake is
 * the point: the plugin cannot reach the host's registries, and asserting the
 * *contribution* rather than the registry's state is what makes these tests survive
 * the move.
 */
const tools = new Map<string, ToolDescriptor>();
const mappers: ElementMapper[] = [];

const getTool = (id: string) => tools.get(id);
const toolForElementType = (type: string) =>
    [...tools.values()].find((tool) => producedElementType(tool.produces) === type);
const ARMED: DrawHintState = { phase: "armed", placed: 0, movable: true };
/** The first line a grammar shows before its first click — which names its interaction. */
const armedHint = (tool: ToolDescriptor, params = {}) =>
    resolveGrammar(tool, params)?.hints(ARMED)[0]?.key;

beforeAll(() =>
    installSymbology({
        registerTool: (tool) => tools.set(tool.id, tool),
        registerElementMapper: (mapper) => mappers.push(mapper as unknown as ElementMapper),
    }),
);

describe("installSymbology — the scene mappers", () => {
    it("draws point symbols with a thumbnail and text toggle", () => {
        expect(
            mappers.map(({ type, textSetting, preview }) => [type, textSetting?.order, !!preview]),
        ).toEqual([["symbol", 70, true]]);
    });
});

describe("installSymbology — contributes its tools to the shared registry", () => {
    it("registers only the point-symbol tool", () => {
        expect(getTool("symbolTool")).toBeDefined();
        expect(getTool("graphicTool")).toBeUndefined();
    });

    it("symbolTool builds its element from the params SIDC, not a shared default", () => {
        const sidc = SIDC.fromString("10061000141211000000");
        const grammar = resolveGrammar(getTool("symbolTool")!, { sidc });

        expect(armedHint(getTool("symbolTool")!, { sidc })).toBe("drawHints.point.place");
        // The created element carries the code up front — `@opencop/elements` has
        // no default symbol code, so nothing seeds it after the fact.
        expect((grammar?.create([0, 0]) as Element).getCode()).toBe("10061000141211000000");
    });

    it("symbolTool falls back to symbology's own default when params are empty", () => {
        const draft = resolveGrammar(getTool("symbolTool")!, {})?.create([0, 0]) as Element;
        expect(draft.getCode()).toBe(SIDC.default().toString());
    });

    it("mounts the point-symbol editor", () => {
        expect(getTool("symbolTool")?.editor).toBeDefined();
        expect(getTool("symbolTool")?.style).toEqual([]);
    });

    it("labels the point-symbol tool from the plugin's own namespace, not the host's id convention", () => {
        expect(getTool("symbolTool")?.labelKey).toBe("symbology:tools.symbol");
    });

    it("maps only point symbols to a tool", () => {
        expect(toolForElementType("symbol")?.id).toBe("symbolTool");
    });
});

describe("symbolTool.onCommit — replaces the hardcoded symbol branch in save()", () => {
    it("records the saved symbol's SIDC in symbology's recents", () => {
        const sidc = SIDC.default().toString();
        const element = {
            getCode: () => sidc,
            getModifiers: () => ({}),
        } as never;

        getTool("symbolTool")?.onCommit?.(element);

        expect(useSymbolHistoryStore.getState().recents[0]?.sidc).toBe(sidc);
    });

    it("does nothing for an element with no code", () => {
        const before = useSymbolHistoryStore.getState().recents.length;
        const element = { getCode: () => undefined, getModifiers: () => ({}) } as never;

        getTool("symbolTool")?.onCommit?.(element);

        expect(useSymbolHistoryStore.getState().recents).toHaveLength(before);
    });
});
