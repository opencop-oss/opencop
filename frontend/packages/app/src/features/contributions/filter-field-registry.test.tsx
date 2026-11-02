// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import type { FilterField, Predicate } from "@opencop/ui";
import { act, render, renderHook, screen } from "~/test";
import { nativeOwner, pluginOwner, __resetContributionFailureLog } from "./contribution-guard";
import {
    registerFilterField,
    getRegisteredFilterFields,
    useRegisteredFilterFields,
} from "./filter-field-registry";

const disposers: Array<() => void> = [];
const register = (...items: Array<() => void>) => disposers.push(...items);

const PLUGIN = pluginOwner("acme.plugin");

/** A well-formed plugin field; tests break one member at a time. */
const field = (overrides: Partial<FilterField> = {}): FilterField =>
    ({
        id: "acme.plugin:code",
        // A key the host bundle has, so the label resolves.
        labelKey: "filter.fields.kind",
        path: "properties.opencop.element.symbol",
        order: 30,
        ...overrides,
    }) as FilterField;

const SAVED: Predicate = {
    path: "properties.opencop.element.symbol",
    operation: "EQUALS",
    values: ["x"],
};

beforeEach(() => {
    __resetContributionFailureLog();
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
});

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
    expect(getRegisteredFilterFields()).toHaveLength(0);
});

describe("filter-field registry — validation", () => {
    it.each([
        ["a host field's id", { id: "kind" }, /reserved by the host/],
        ["a custom attribute's id", { id: "attribute:callsign" }, /reserved by the host/],
        ["a label key that does not resolve", { labelKey: "acme:nowhere" }, /does not resolve/],
        ["a notice key that does not resolve", { noticeKey: "acme:nowhere" }, /does not resolve/],
        [
            "a path no evaluator accepts",
            { path: "properties.opencop.location" },
            /not a filterable property/,
        ],
        ["an empty operator list", { operators: [] }, /at least one operator/],
        ["an unknown operator", { operators: ["MATCHES"] }, /operator "MATCHES" unknown/],
        ["a prototype key as operator", { operators: ["toString"] }, /operator "toString" unknown/],
        ["an order that is not a finite number", { order: Number.NaN }, /order missing/],
        [
            "a value editor that is not a component",
            { ValueEditor: "Editor" },
            /ValueEditor is not a component/,
        ],
        ["a member that is not a function", { claims: "yes" }, /claims is not a function/],
    ] as const)("refuses %s", (_case, overrides, reason) => {
        expect(() =>
            registerFilterField(PLUGIN, field(overrides as unknown as Partial<FilterField>)),
        ).toThrow(reason);
    });

    it("refuses a field without claims on a path the host's own fields hold", () => {
        expect(() =>
            registerFilterField(PLUGIN, field({ path: "properties.opencop.displayName" })),
        ).toThrow(/must declare claims/);
        expect(() =>
            registerFilterField(PLUGIN, field({ path: "properties.opencop.attributes.hull" })),
        ).toThrow(/must declare claims/);
    });

    it("accepts a field on a host path when it declares claims", () => {
        register(
            registerFilterField(
                PLUGIN,
                field({ path: "properties.opencop.displayName", claims: () => null }),
            ),
        );

        expect(getRegisteredFilterFields().map((f) => f.path)).toEqual([
            "properties.opencop.displayName",
        ]);
    });

    it("refuses an id another owner holds", () => {
        register(registerFilterField(PLUGIN, field()));

        expect(() => registerFilterField(PLUGIN, field())).toThrow(/already held by "acme.plugin"/);
    });

    it("accepts a native subject's field under its own name", () => {
        register(
            registerFilterField(
                nativeOwner("opencop.shapes"),
                field({ id: "opencop.shapes:hull" }),
            ),
        );

        expect(getRegisteredFilterFields().map((f) => f.id)).toEqual(["opencop.shapes:hull"]);
    });
});

describe("filter-field registry — containment", () => {
    const throwing = () => {
        throw new Error("boom");
    };

    it("treats a throwing claim as no claim", () => {
        register(registerFilterField(PLUGIN, field({ claims: throwing })));

        expect(getRegisteredFilterFields()[0]?.claims?.(SAVED, undefined)).toBeNull();
    });

    it("turns a throwing companions into 'cannot save this row now'", () => {
        register(registerFilterField(PLUGIN, field({ companions: throwing })));

        expect(getRegisteredFilterFields()[0]?.companions?.(SAVED)).toBeUndefined();
    });

    it("keeps the generic summary when formatValue throws", () => {
        register(registerFilterField(PLUGIN, field({ formatValue: throwing })));

        expect(
            getRegisteredFilterFields()[0]?.formatValue?.(SAVED, ((key: string) => key) as never),
        ).toBeUndefined();
    });

    it("leaves a row unchanged when newDraft throws", () => {
        register(registerFilterField(PLUGIN, field({ newDraft: throwing })));

        expect(getRegisteredFilterFields()[0]?.newDraft?.("row")).toBeUndefined();
    });

    it("contains a throwing value editor and reports it with its owner", () => {
        vi.spyOn(console, "error").mockImplementation(() => undefined);
        const report = vi.spyOn(Logger.get("plugins"), "error");
        const Broken = () => {
            throw new Error("editor broke");
        };
        register(registerFilterField(PLUGIN, field({ ValueEditor: Broken })));
        const Contained = getRegisteredFilterFields()[0]?.ValueEditor;
        if (!Contained) throw new Error("no value editor");

        render(
            <div>
                <span data-testid="row-survives" />
                <Contained
                    predicate={{ id: "r", operator: undefined, value: undefined }}
                    setPredicate={() => undefined}
                />
            </div>,
        );

        expect(screen.getByTestId("row-survives")).toBeInTheDocument();
        expect(report.mock.calls.flat().join(" ")).toContain("acme.plugin");
    });
});

describe("filter-field registry — re-arming", () => {
    it("calls a member the breaker stopped again once the field is registered again", () => {
        let failing = true;
        let calls = 0;
        const claims = () => {
            calls += 1;
            if (failing) throw new Error("boom");
            return { folds: 0 as const };
        };
        const dispose = registerFilterField(PLUGIN, field({ claims }));
        register(dispose);
        for (let i = 0; i < 100; i++) getRegisteredFilterFields()[0]?.claims?.(SAVED, undefined);
        // The breaker opened: not every call reached the member.
        expect(calls).toBeLessThan(100);

        failing = false;
        const callsWhileTripped = calls;
        expect(getRegisteredFilterFields()[0]?.claims?.(SAVED, undefined)).toBeNull();
        expect(calls).toBe(callsWhileTripped);

        dispose();
        register(registerFilterField(PLUGIN, field({ claims })));

        expect(getRegisteredFilterFields()[0]?.claims?.(SAVED, undefined)).toEqual({ folds: 0 });
        expect(calls).toBe(callsWhileTripped + 1);
    });
});

describe("filter-field registry — removal", () => {
    it("removes the field on dispose, and readers re-render", () => {
        const { result } = renderHook(() => useRegisteredFilterFields());
        let dispose: () => void = () => undefined;

        act(() => {
            dispose = registerFilterField(PLUGIN, field());
        });
        expect(result.current.map((f) => f.id)).toEqual(["acme.plugin:code"]);

        act(() => dispose());
        expect(result.current).toEqual([]);
    });
});
