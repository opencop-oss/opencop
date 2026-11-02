// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { Point } from "@opencop/elements";
import type { ElementMapper, MapperState } from "@opencop/contributions";
import {
    __resetContributionFailureLog,
    nativeOwner,
    pluginOwner,
} from "~/features/contributions/contribution-guard";
import {
    getElementMappers,
    getTextSettings,
    mapperFor,
    registerElementMapper,
    resolveElementPreview,
    subscribeElementMappers,
} from "./element-mapper-registry";

const mapper = (type: string, extra: Partial<ElementMapper> = {}): ElementMapper => ({
    type,
    map: () => [],
    ...extra,
});

const cleanups: (() => void)[] = [];
const register = (m: ElementMapper, owner = nativeOwner("opencop.test")) => {
    const remove = registerElementMapper(owner, m);
    cleanups.push(remove);
    return remove;
};

afterEach(() => {
    cleanups.splice(0).forEach((cleanup) => cleanup());
    __resetContributionFailureLog();
});

const state = {
    units: { angle: "degrees", angleOrientation: "cw", measurement: "METRIC" },
    icons: { size: 32 },
    text: { size: 15, zoomThreshold: 5 },
    view: { bounds: [0, 0, 1, 1], latitude: 0, zoom: 3 },
    emphasis: { outlineColor: "#ff0000" },
} as unknown as MapperState;

describe("element mapper registry", () => {
    it("adds a mapper for a type, and removes it with its disposer", () => {
        const remove = register(mapper("test:radar"));
        expect(mapperFor("test:radar")?.type).toBe("test:radar");
        expect(getElementMappers()).toHaveLength(1);

        remove();
        expect(mapperFor("test:radar")).toBeUndefined();
        expect(getElementMappers()).toEqual([]);
    });

    it("refuses a second mapper for a held type, naming the owner that holds it", () => {
        register(mapper("test:radar"), nativeOwner("opencop.shapes"));

        expect(() => register(mapper("test:radar"), pluginOwner("acme"))).toThrow(
            /"test:radar" is already held by "opencop.shapes"/,
        );
        expect(getElementMappers()).toHaveLength(1);
    });

    it("refuses a malformed mapper", () => {
        expect(() => register({ type: "", map: () => [] })).toThrow(/type missing/);
        expect(() => register({ type: "x" } as unknown as ElementMapper)).toThrow(/map missing/);
        expect(() => register(mapper("x", { textSetting: { labelKey: "" } }))).toThrow(
            /textSetting.labelKey missing/,
        );
    });

    it("removes only its own mapper when its disposer runs late", () => {
        const removeFirst = register(mapper("test:radar"));
        removeFirst();
        register(mapper("test:radar", { textSetting: { labelKey: "second" } }));

        removeFirst();

        expect(mapperFor("test:radar")?.textSetting?.labelKey).toBe("second");
    });

    it("notifies subscribers of each registration and removal", () => {
        const listener = vi.fn();
        const unsubscribe = subscribeElementMappers(listener);

        const remove = register(mapper("test:radar"));
        remove();
        unsubscribe();
        register(mapper("test:sonar"));

        expect(listener).toHaveBeenCalledTimes(2);
    });

    it("contains every member: a throw maps, selects and previews nothing", () => {
        const boom = () => {
            throw new Error("boom");
        };
        register(
            mapper("test:radar", { map: boom, preview: boom, selector: { select: boom } } as never),
        );
        const contained = mapperFor("test:radar")!;
        const element = Point.create({ position: [0, 0] });

        expect(contained.map(element, undefined)).toEqual([]);
        expect(contained.selector?.select(state)).toBeUndefined();
        expect(contained.preview?.(element)).toBeNull();
    });

    it("carries a mapper's textIsContent through containment", () => {
        // `contain()` rebuilds the mapper field by field, so a field it forgets
        // is dropped silently — and the pipeline then treats content as a
        // label, which hides it below the label zoom threshold.
        register(mapper("test:note", { textIsContent: true }));
        expect(mapperFor("test:note")?.textIsContent).toBe(true);

        register(mapper("test:radar"));
        expect(mapperFor("test:radar")?.textIsContent).toBeUndefined();
    });

    it("hands a selector only the mapper state, whatever else the host passes", () => {
        const select = vi.fn((s: MapperState) => s);
        register(mapper("test:radar", { selector: { select } } as never));

        mapperFor("test:radar")!.selector!.select(state);

        expect(Object.keys(select.mock.calls[0][0]).sort()).toEqual([
            "icons",
            "text",
            "units",
            "view",
        ]);
    });

    it("resolves an element's preview from its type's mapper", () => {
        const image = {} as CanvasImageSource;
        register(mapper("point", { preview: () => image }));

        expect(resolveElementPreview(Point.create({ position: [0, 0] }))).toBe(image);
    });

    it("lists text toggles by declared order", () => {
        register(mapper("b", { textSetting: { labelKey: "b", order: 20 } }));
        register(mapper("a", { textSetting: { labelKey: "a", order: 10 } }));
        register(mapper("c"));

        expect(getTextSettings()).toEqual([
            { type: "a", labelKey: "a", order: 10 },
            { type: "b", labelKey: "b", order: 20 },
        ]);
        expect(getTextSettings()).toBe(getTextSettings());
    });
});
