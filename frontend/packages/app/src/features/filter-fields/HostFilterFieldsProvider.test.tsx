// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { useFilterFields, type FilterField } from "@opencop/ui";
import { act, renderHook } from "~/test";
import { pluginOwner } from "~/features/contributions/contribution-guard";
import { registerFilterField } from "~/features/contributions/filter-field-registry";
import { useCustomAttributesStore } from "./custom-attributes.store";

const ids = (fields: readonly FilterField[]) => fields.map((field) => field.id);

afterEach(() => {
    act(() => useCustomAttributesStore.setState({ names: [] }));
});

describe("HostFilterFieldsProvider", () => {
    it("orders the host's fields, contributed fields and custom attributes by `order`", () => {
        const dispose = registerFilterField(pluginOwner("acme.plugin"), {
            id: "acme.plugin:code",
            labelKey: "filter.fields.kind",
            path: "properties.opencop.element.symbol",
            order: 15,
        });
        act(() => useCustomAttributesStore.getState().actions.addAttribute("hull"));

        const { result } = renderHook(() => useFilterFields());

        expect(ids(result.current.fields)).toEqual([
            "kind",
            "acme.plugin:code",
            "displayName",
            "attribute:hull",
        ]);
        dispose();
    });

    it("keeps a custom attribute named like a built-in field beside it, not over it", () => {
        const { result } = renderHook(() => useFilterFields());

        act(() => {
            result.current.createField?.("kind");
        });

        expect(ids(result.current.fields)).toEqual(["kind", "displayName", "attribute:kind"]);
        expect(result.current.fields[0]).toMatchObject({ labelKey: "filter.fields.kind" });
    });

    it("creates and removes custom attributes by field id", () => {
        const { result } = renderHook(() => useFilterFields());

        let id = "";
        act(() => {
            id = result.current.createField?.("callsign") ?? "";
        });
        expect(id).toBe("attribute:callsign");
        expect(ids(result.current.fields)).toContain(id);

        act(() => result.current.removeField?.(id));
        expect(ids(result.current.fields)).not.toContain(id);
    });

    it("reads back an attribute this browser never saw", () => {
        const { result } = renderHook(() => useFilterFields());

        expect(
            result.current.fieldFor?.({
                path: "properties.opencop.attributes.callsign",
                operation: "EXISTS",
            }),
        ).toMatchObject({ id: "attribute:callsign", labelText: "callsign" });
        expect(
            result.current.fieldFor?.({
                path: "properties.opencop.element.symbol",
                operation: "EXISTS",
            }),
        ).toBeUndefined();
    });

    it("accepts as an attribute name only what the backend accepts", () => {
        const { result } = renderHook(() => useFilterFields());

        expect(result.current.isValidFieldName?.("call_sign-2")).toBe(true);
        expect(result.current.isValidFieldName?.("call.sign")).toBe(false);
        expect(result.current.isValidFieldName?.("x".repeat(65))).toBe(false);
    });
});
