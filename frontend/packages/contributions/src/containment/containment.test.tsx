// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/react";
import type { FilterField } from "@opencop/ui";
import { CONSECUTIVE_FAILURE_LIMIT, createContainment } from "./containment.ts";
import { containFilterField } from "./kinds.ts";
import type { ContainmentPolicy } from "./members.ts";
import { pluginOwner } from "./origin.ts";

const OWNER = pluginOwner("acme.plugin");
const ORIGIN = {
    owner: OWNER,
    contribution: "filter-field/acme.plugin:sector",
    phase: "formatValue",
};

afterEach(() => {
    vi.restoreAllMocks();
});

function setup() {
    const log = { error: vi.fn(), warn: vi.fn() };
    const failures: unknown[] = [];
    const containment = createContainment({ log, onFailure: (_, error) => failures.push(error) });
    return { log, failures, containment };
}

const boom = () => {
    throw new Error("boom");
};

describe("the log lines operators and /admin/plugins read", () => {
    it("are worded as they always were", () => {
        const { log, containment } = setup();
        for (let i = 0; i < CONSECUTIVE_FAILURE_LIMIT; i++)
            containment.runContribution(ORIGIN, boom, () => undefined);

        expect(log.error.mock.calls.map(([message]) => message)).toEqual([
            'contribution "acme.plugin:filter-field/acme.plugin:sector:formatValue" threw; the host contained it',
            `contribution "acme.plugin:filter-field/acme.plugin:sector:formatValue" threw ${CONSECUTIVE_FAILURE_LIMIT} times in a row; the host has stopped calling it and will use its fallback until the contribution is re-registered`,
        ]);
    });
});

describe("guard", () => {
    it("contains exactly as runContribution does: trips after the limit, stays tripped, recovers on rearm", () => {
        const { containment } = setup();
        const call = vi.fn<() => string>(boom);
        const guarded = containment.guard(ORIGIN, call, () => "fallback");

        for (let i = 0; i < CONSECUTIVE_FAILURE_LIMIT; i++) expect(guarded()).toBe("fallback");
        expect(guarded()).toBe("fallback");
        expect(call).toHaveBeenCalledTimes(CONSECUTIVE_FAILURE_LIMIT);

        containment.rearmContributions(ORIGIN.contribution);
        call.mockImplementation(() => "value");
        expect(guarded()).toBe("value");
    });

    it("resets the streak on a success, so intermittent throws never trip", () => {
        const { containment } = setup();
        let n = 0;
        const guarded = containment.guard(
            ORIGIN,
            () => (n++ % 2 === 0 ? boom() : "value"),
            () => "fallback",
        );
        for (let i = 0; i < CONSECUTIVE_FAILURE_LIMIT * 3; i++) guarded();
        n = 1;
        expect(guarded()).toBe("value");
    });

    it("passes the call's arguments to the fallback", () => {
        const { containment } = setup();
        const guarded = containment.guard(
            ORIGIN,
            (x: number) => (x > 0 ? boom() : x),
            (_e, x) => x * 2,
        );
        expect(guarded(21)).toBe(42);
    });
});

describe("createContainment", () => {
    it("gives each containment its own state: two share nothing", () => {
        const a = setup();
        const b = setup();
        for (let i = 0; i < CONSECUTIVE_FAILURE_LIMIT; i++)
            a.containment.runContribution(ORIGIN, boom, () => undefined);

        const call = vi.fn(() => "value");
        expect(b.containment.runContribution(ORIGIN, call, () => "fallback")).toBe("value");
        expect(b.failures).toEqual([]);
        expect(a.failures).toHaveLength(CONSECUTIVE_FAILURE_LIMIT);
    });

    it("reports every failure to onFailure, though the log gets only the first", () => {
        const { log, failures, containment } = setup();
        containment.runContribution(ORIGIN, boom, () => undefined);
        containment.runContribution(ORIGIN, boom, () => undefined);
        expect(failures).toHaveLength(2);
        expect(log.error).toHaveBeenCalledTimes(1);
    });

    it("contains a rendered contribution: it renders nothing and is reported", () => {
        const { failures, containment } = setup();
        vi.spyOn(console, "error").mockImplementation(() => undefined);
        const Broken = () => boom();
        const Contained = containment.containComponent(ORIGIN, Broken);

        const { container } = render(<Contained />);

        expect(container).toBeEmptyDOMElement();
        expect(failures).toHaveLength(1);
    });
});

describe("a contribution type's containment", () => {
    it("wraps each contributed member with the type's own fallback", () => {
        const { containment } = setup();
        const field = containFilterField(containment, OWNER, {
            id: "acme.plugin:sector",
            labelKey: "acme.plugin:sector",
            path: "properties.opencop.element.symbol",
            order: 60,
            formatValue: boom,
            claims: boom,
        });

        expect(field.formatValue?.({} as never, ((key: string) => key) as never)).toBeUndefined();
        expect(field.claims?.({} as never, undefined)).toBeNull();
    });

    it("must name every member that holds code", () => {
        // @ts-expect-error — `claims` has no policy, so the declaration does not compile.
        const incomplete: ContainmentPolicy<FilterField> = {
            newDraft: { fallback: () => undefined },
            formatValue: { fallback: () => undefined },
            companions: { fallback: () => undefined },
            ValueEditor: { render: true },
        };
        expect(incomplete).toBeDefined();
    });

    it("renders only a member that is a component", () => {
        const wrong: ContainmentPolicy<FilterField> = {
            newDraft: { fallback: () => undefined },
            formatValue: { fallback: () => undefined },
            companions: { fallback: () => undefined },
            // @ts-expect-error — `claims` is a function the host calls, not a component it renders.
            claims: { render: true },
            ValueEditor: { render: true },
        };
        expect(wrong).toBeDefined();
    });
});
