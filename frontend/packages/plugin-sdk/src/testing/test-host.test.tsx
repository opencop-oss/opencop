// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeAll, describe, expect, it } from "vitest";
import { act, screen } from "@testing-library/react";
import { useTranslation } from "react-i18next";
import { useToolSession } from "@opencop/tools";
import { useDisplayPreferences, useFilterFields } from "@opencop/ui";
import type { ContributionContext } from "@opencop/contributions";
import { createTestHost, type TestHost } from "./index.ts";
import { heldIn, unclaimedFailures } from "./test-host.tsx";

const ID = "opencop.fixture";
const BUNDLE = {
    namespace: "translation",
    resources: {
        en: { fixture: { label: "Sector", greeting: "Hello" } },
        fr: { fixture: { label: "Secteur", greeting: "Bonjour" } },
    },
};

/** A key from the fixture bundle; the host's typed keys do not know it. */
const key = (k: string) => k as never;

function Editor() {
    const { t } = useTranslation();
    const { phase } = useToolSession();
    const { measurementSystem } = useDisplayPreferences();
    const { fields } = useFilterFields();
    return (
        <p>{`${t(key("fixture.greeting"))} · ${phase} · ${measurementSystem} · ${fields.map((f) => f.id).join(",")}`}</p>
    );
}

function Broken(): never {
    throw new Error("editor broke");
}

/** A native-shaped install: a tool with an editor, and a filter field. */
const sectorField = (host: TestHost) =>
    host.contributions.filterFields.getEntries().find(({ field }) => field.id === `${ID}:sector`)!
        .field;

const installFixture = (ctx: ContributionContext) => {
    ctx.registerTool({ id: "fixture-tool", label: "Fixture", icon: () => null, editor: Editor });
    ctx.registerFilterField({
        id: `${ID}:sector`,
        labelKey: "fixture.label",
        path: "properties.opencop.element.symbol",
        order: 60,
        formatValue: () => {
            throw new Error("formatValue broke");
        },
    });
};

describe("createTestHost", () => {
    it("installs the plugin and renders its UI under every host capability", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });

        expect(host.problems).toEqual([]);
        host.render(host.contributions.tools.get("fixture-tool")!.editor!);

        expect(screen.getByText(`Hello · idle · IMPERIAL · ${ID}:sector`)).toBeInTheDocument();
    });

    it("hands a field registered after the first render to what is rendered", () => {
        let registerLate: (() => void) | undefined;
        const host = createTestHost({
            id: ID,
            translations: [BUNDLE],
            install: (ctx) => {
                registerLate = () =>
                    void ctx.registerFilterField({
                        id: `${ID}:late`,
                        labelKey: "fixture.label",
                        path: "properties.opencop.element.symbol",
                        order: 70,
                    });
            },
        });
        const { result } = host.renderHook(() => useFilterFields().fields.map((f) => f.id));
        expect(result.current).toEqual([]);

        act(() => registerLate!());

        expect(result.current).toEqual([`${ID}:late`]);
    });

    it("overrides a capability for one render", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        host.render(host.contributions.tools.get("fixture-tool")!.editor!, {
            capabilities: {
                toolSession: { phase: "draw" },
                displayPreferences: { measurementSystem: "METRIC" },
            },
        });
        expect(screen.getByText(`Hello · draw · METRIC · ${ID}:sector`)).toBeInTheDocument();
    });

    it("updates capabilities under what is already rendered, as the host does", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        host.render(host.contributions.tools.get("fixture-tool")!.editor!);

        host.update({ toolSession: { phase: "edit" } });

        expect(screen.getByText(`Hello · edit · IMPERIAL · ${ID}:sector`)).toBeInTheDocument();
    });

    it("keeps what a display-preference setter sets, as the host does", () => {
        function AngleUnit() {
            const { angleUnit, setAngleUnit } = useDisplayPreferences();
            return (
                <button type="button" onClick={() => setAngleUnit("mils")}>
                    {angleUnit}
                </button>
            );
        }
        const host = createTestHost({ id: ID, install: installFixture });
        host.render(<AngleUnit />);

        act(() => screen.getByRole("button").click());

        expect(screen.getByRole("button")).toHaveTextContent("mils");
    });

    it("switches its own language", async () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        await host.setLocale("fr");
        host.render(host.contributions.tools.get("fixture-tool")!.editor!);
        expect(screen.getByText(/^Bonjour/)).toBeInTheDocument();
    });

    it("refuses a registration the host's rules refuse, and reports the problem", () => {
        const host = createTestHost({
            id: ID,
            install: (ctx) =>
                ctx.registerFilterField({
                    id: `${ID}:sector`,
                    labelKey: "fixture.nowhere",
                    path: "properties.opencop.location",
                    order: 60,
                }),
            translations: [BUNDLE],
        });

        expect(host.problems.map((problem) => problem.rule)).toEqual([
            "filterFields.label-key-unresolved",
            "filterFields.path-not-filterable",
        ]);
        expect(host.contributions.filterFields.getEntries().length).toBe(0);
    });

    it("contains a throw as the host does, and keeps it for the test to take", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        const field = sectorField(host);

        expect(field.formatValue?.({} as never, ((key: string) => key) as never)).toBeUndefined();

        const failures = host.takeFailures();
        expect(failures.map((failure) => failure.origin)).toEqual([
            `native:${ID}:filter-field/${ID}:sector:formatValue`,
        ]);
        expect(host.failures).toEqual([]);
    });

    it("contains a registered component that throws: it renders nothing", () => {
        const host = createTestHost({
            id: ID,
            install: (ctx) =>
                ctx.registerTool({
                    id: "broken-tool",
                    label: "Broken",
                    icon: () => null,
                    editor: Broken,
                }),
        });
        const { container } = host.render(host.contributions.tools.get("broken-tool")!.editor!);

        // Only the theme's color-mode script, which the host renders too.
        expect(container.querySelector(":scope > :not(script)")).toBeNull();
        expect(host.takeFailures().map((failure) => failure.origin)).toEqual([
            `native:${ID}:broken-tool:editor`,
        ]);
    });

    it("removes everything on uninstall", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        host.uninstall();
        expect(
            host.contributions.tools.getAll().length +
                host.contributions.filterFields.getEntries().length,
        ).toBe(0);
    });

    it("catches a disposer that leaks: what it left running registers after uninstall", () => {
        const listeners = new Set<() => void>();
        const flagTurnsOn = () => listeners.forEach((listener) => listener());
        const host = createTestHost({
            id: ID,
            install: (ctx) => {
                const onFlag = () =>
                    ctx.registerTool({
                        id: "late-tool",
                        label: "Late",
                        icon: () => null,
                        content: () => null,
                    });
                listeners.add(onFlag);
                // Leaks: the disposer forgets to stop listening.
                return () => undefined;
            },
        });

        host.uninstall();
        flagTurnsOn();

        expect(host.takeFailures().map((failure) => failure.origin)).toEqual([
            `[native-install]: native subject "${ID}" registered after it was disposed`,
        ]);
        expect(host.contributions.tools.getAll().length).toBe(0);
    });

    it("fails uninstall when the plugin's own disposer throws", () => {
        const host = createTestHost({
            id: ID,
            install: () => () => {
                throw new Error("dispose broke");
            },
        });
        expect(() => host.uninstall()).toThrow(`${ID} did not uninstall cleanly`);
    });

    it("gives each test host its own state: two share nothing", () => {
        const a = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        const b = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });

        sectorField(a).formatValue?.({} as never, ((k: string) => k) as never);
        void a.setLocale("fr");

        expect(a.takeFailures()).toHaveLength(1);
        expect(b.failures).toEqual([]);
        expect(b.i18n.language).toBe("en");
        a.uninstall();
        expect(b.contributions.tools.getAll().length).toBe(1);
    });

    it("reads the registries as the host's consumers do, with nothing that writes", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });

        expect(host.contributions.tools.get("fixture-tool")?.label).toBe("Fixture");
        for (const registry of Object.values(host.contributions))
            expect(
                Object.keys(registry).filter((member) => /^(register|clear)/.test(member)),
            ).toEqual([]);
    });
});

describe("a contained throw the test does not take", () => {
    // `it.fails` passes only if the test fails: the test host's end-of-test check must fail it.
    it.fails("fails the test it happened in", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        sectorField(host).formatValue?.({} as never, ((k: string) => k) as never);
    });
});

describe("a disposer that throws in a test that never uninstalls", () => {
    // `it.fails` passes only if the test fails: the end-of-test teardown must fail it.
    it.fails("fails the test it happened in", () => {
        createTestHost({
            id: ID,
            install: () => () => {
                throw new Error("disposer threw");
            },
        });
    });
});

describe("heldIn", () => {
    it("lists what each registry holds, and nothing once uninstalled", () => {
        const host = createTestHost({ id: ID, install: installFixture, translations: [BUNDLE] });
        expect(heldIn(host.contributions)).toEqual([
            "tools: fixture-tool",
            `filterFields: ${ID}:sector`,
        ]);
        host.uninstall();
        expect(heldIn(host.contributions)).toEqual([]);
    });
});

describe("unclaimedFailures", () => {
    it("is nothing when the test took every throw", () => {
        expect(unclaimedFailures(ID, [])).toBeUndefined();
    });

    it("names each throw the test did not take by its origin", () => {
        const error = new Error("boom");
        const verdict = unclaimedFailures(ID, [
            { origin: "filterField(sector).formatValue", error },
            { origin: "tool(draw).icon", error },
        ]);
        expect(verdict?.message).toBe(
            `${ID}: contained throws the test did not take: filterField(sector).formatValue, tool(draw).icon`,
        );
        expect(verdict?.errors).toEqual([error, error]);
    });
});

describe("a test host made outside a test", () => {
    let refusal: unknown;
    beforeAll(() => {
        try {
            createTestHost({ id: ID, install: installFixture });
        } catch (error) {
            refusal = error;
        }
    });

    it("is refused: a test host belongs to one test", () => {
        expect(String(refusal)).toContain("call it inside a test or a beforeEach hook");
    });
});
