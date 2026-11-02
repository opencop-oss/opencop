// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { nativeOwner, pluginOwner } from "~/features/contributions/contribution-guard";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { Line, Point } from "@opencop/elements";
import { act, render, renderHook, screen } from "~/test";
import {
    getDetailsSurfaceCounts,
    registerDetailsEditForm,
    registerDetailsHeader,
    registerDetailsInfoSection,
    registerDetailsPosition,
    resolveDetailsEditForms,
    resolveDetailsHeader,
    resolveDetailsInfoSections,
    resolveDetailsPosition,
    useDetailsHeader,
} from "./details-registry";
import { __resetContributionFailureLog } from "./contribution-guard";

const disposers: Array<() => void> = [];
const register = (...items: Array<() => void>) => disposers.push(...items);

const Glyph = () => <svg data-testid="glyph" />;
const OtherGlyph = () => <svg data-testid="other-glyph" />;
const element = Point.create({ position: [1, 2] });
const PANEL = "panel";

beforeEach(() => {
    __resetContributionFailureLog();
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
});

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
    expect(getDetailsSurfaceCounts()).toEqual({
        headers: 0,
        positions: 0,
        editForms: 0,
        infoSections: 0,
    });
});

describe("details header", () => {
    it("resolves the type's registration, else the host default", () => {
        register(
            registerDetailsHeader(nativeOwner("opencop.a"), { type: "symbol", typeIcon: Glyph }),
            registerDetailsHeader(nativeOwner("opencop.b"), { type: "graphic", iconFrame: "none" }),
        );

        // The glyph comes back contained, so it is a wrapper, not `Glyph` itself.
        expect(resolveDetailsHeader("symbol").typeIcon).toBeDefined();
        expect(resolveDetailsHeader("symbol").iconFrame).toBeUndefined();
        expect(resolveDetailsHeader("graphic").iconFrame).toBe("none");
        expect(resolveDetailsHeader("line")).toEqual({ typeIcon: undefined, iconFrame: undefined });
    });

    it("refuses a type another owner holds, native or plugin", () => {
        register(
            registerDetailsHeader(nativeOwner("opencop.a"), { type: "graphic", iconFrame: "none" }),
        );

        expect(() =>
            registerDetailsHeader(pluginOwner("acme.plugin"), {
                type: "graphic",
                typeIcon: OtherGlyph,
            }),
        ).toThrow('"graphic" is already held by "opencop.a"');
    });

    it.each([
        [{ type: "" }, "type missing"],
        [{ iconFrame: "none" }, "type missing"],
        [{ type: "line", iconFrame: "dashed" }, 'iconFrame "dashed" unknown'],
        [{ type: "line", typeIcon: "svg" }, "typeIcon is not a component"],
    ])("refuses %o", (contribution, error) => {
        expect(() =>
            registerDetailsHeader(nativeOwner("opencop.a"), contribution as never),
        ).toThrow(error);
    });

    it("contains a glyph that throws: it renders nothing and the panel survives", () => {
        const Broken = () => {
            throw new Error("glyph broke");
        };
        register(
            registerDetailsHeader(nativeOwner("opencop.a"), { type: "line", typeIcon: Broken }),
        );
        const { typeIcon: Icon } = resolveDetailsHeader("line");
        vi.spyOn(console, "error").mockImplementation(() => undefined);

        render(
            <div>
                <span>{PANEL}</span>
                {Icon && <Icon color="red" />}
            </div>,
        );

        expect(screen.getByText(PANEL)).toBeInTheDocument();
    });
});

describe("details position", () => {
    it("resolves the element's type; undefined when no registration applies", () => {
        register(
            registerDetailsPosition(nativeOwner("opencop.a"), {
                type: "point",
                position: () => ({ position: [3, 4], labelKey: "base" }),
            }),
        );

        expect(resolveDetailsPosition(element)).toEqual({ position: [3, 4], labelKey: "base" });
        expect(resolveDetailsPosition(Line.create({ positions: [0, 0, 1, 1] }))).toBeUndefined();
    });

    it("refuses a type another owner holds", () => {
        const position = () => null;
        register(registerDetailsPosition(nativeOwner("opencop.a"), { type: "point", position }));

        expect(() =>
            registerDetailsPosition(nativeOwner("opencop.b"), { type: "point", position }),
        ).toThrow('"point" is already held by "opencop.a"');
    });

    it("contains a position that throws as no row", () => {
        register(
            registerDetailsPosition(nativeOwner("opencop.a"), {
                type: "point",
                position: () => {
                    throw new Error("position broke");
                },
            }),
        );

        expect(resolveDetailsPosition(element)).toBeNull();
    });
});

describe("details edit forms", () => {
    const form = (id: string, type: string, order: number) => ({
        id,
        type,
        order,
        Component: Glyph,
    });

    it("lists the type's forms by order, and only that type's", () => {
        register(
            registerDetailsEditForm(nativeOwner("opencop.a"), form("second", "symbol", 20)),
            registerDetailsEditForm(nativeOwner("opencop.b"), form("first", "symbol", 10)),
            registerDetailsEditForm(nativeOwner("opencop.a"), form("lines", "line", 10)),
        );

        expect(resolveDetailsEditForms("symbol").map((f) => f.id)).toEqual(["first", "second"]);
        expect(resolveDetailsEditForms("line").map((f) => f.id)).toEqual(["lines"]);
        expect(resolveDetailsEditForms("circle")).toEqual([]);
    });

    it("refuses an id already registered for the type, and allows it for another type", () => {
        register(registerDetailsEditForm(nativeOwner("opencop.a"), form("dup", "line", 1)));

        expect(() =>
            registerDetailsEditForm(nativeOwner("opencop.b"), form("dup", "line", 2)),
        ).toThrow('id "dup" is already registered for "line"');
        register(registerDetailsEditForm(nativeOwner("opencop.a"), form("dup", "polygon", 1)));
    });

    it("refuses a form without a type", () => {
        expect(() =>
            registerDetailsEditForm(nativeOwner("opencop.a"), {
                id: "untyped",
                order: 1,
                Component: Glyph,
            } as never),
        ).toThrow("type missing");
    });
});

describe("details information sections", () => {
    it("lists the type's sections by order", () => {
        register(
            registerDetailsInfoSection(nativeOwner("opencop.a"), {
                id: "late",
                type: "polygon",
                order: 60,
                Component: Glyph,
            }),
            registerDetailsInfoSection(nativeOwner("opencop.a"), {
                id: "shapes",
                type: "polygon",
                order: 30,
                Component: Glyph,
            }),
        );

        expect(resolveDetailsInfoSections("polygon").map((s) => s.id)).toEqual(["shapes", "late"]);
        expect(resolveDetailsInfoSections("symbol")).toEqual([]);
    });
});

describe("contained edit forms and information sections", () => {
    const bad = Point.create({ position: [3, 4] });
    const good = Point.create({ position: [5, 6] });
    const OK = "renders";
    const SIBLING = "sibling";
    const Breaks = ({ element: shown }: { element: unknown }) => {
        if (shown === bad) throw new Error("form broke");
        return <span>{OK}</span>;
    };
    const Sibling = () => <span>{SIBLING}</span>;
    // Each surface renders its own components, so their props stay typed per surface.
    const surfaces = [
        {
            surface: "details-edit-form",
            register: registerDetailsEditForm,
            show: (shown: typeof bad) =>
                resolveDetailsEditForms("point").map(({ id, Component }) => (
                    <Component key={id} element={shown} canEditGeometry canEditProperties />
                )),
        },
        {
            surface: "details-info-section",
            register: registerDetailsInfoSection,
            show: (shown: typeof bad) =>
                resolveDetailsInfoSections("point").map(({ id, Component }) => (
                    <Component key={id} element={shown} />
                )),
        },
    ] as const;

    it.each(surfaces)(
        "$surface: a component that throws renders nothing, is logged against its owner, and spares its sibling",
        ({ surface, register: registerOne, show }) => {
            vi.spyOn(console, "error").mockImplementation(() => undefined);
            register(
                registerOne(nativeOwner("opencop.a"), {
                    id: "broken",
                    type: "point",
                    order: 10,
                    Component: Breaks,
                }),
                registerOne(nativeOwner("opencop.b"), {
                    id: "sibling",
                    type: "point",
                    order: 20,
                    Component: Sibling,
                }),
            );

            render(<div>{show(bad)}</div>);

            expect(screen.getByText(SIBLING)).toBeInTheDocument();
            expect(screen.queryByText(OK)).not.toBeInTheDocument();
            expect(String(vi.mocked(Logger.get("plugins").error).mock.calls[0]?.[0])).toContain(
                `native:opencop.a:${surface}/point/broken:Component`,
            );
        },
    );

    it.each(surfaces)(
        "$surface: after throwing for one element, it renders for the next",
        ({ register: registerOne, show }) => {
            vi.spyOn(console, "error").mockImplementation(() => undefined);
            register(
                registerOne(nativeOwner("opencop.a"), {
                    id: "broken",
                    type: "point",
                    order: 10,
                    Component: Breaks,
                }),
            );

            const { rerender } = render(<div>{show(bad)}</div>);
            expect(screen.queryByText(OK)).not.toBeInTheDocument();

            rerender(<div>{show(good)}</div>);
            expect(screen.getByText(OK)).toBeInTheDocument();
        },
    );
});

describe("panel hooks", () => {
    it("re-resolve when a registration for the open element's type arrives", () => {
        const { result } = renderHook(() => useDetailsHeader(element));
        expect(result.current.iconFrame).toBeUndefined();

        act(() => {
            register(
                registerDetailsHeader(nativeOwner("opencop.a"), {
                    type: "point",
                    iconFrame: "none",
                }),
            );
        });

        expect(result.current.iconFrame).toBe("none");
    });
});
