// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { ContributionBoundary } from "./ContributionBoundary";
import {
    __resetContributionFailureLog,
    describeOrigin,
    nativeOwner,
    pluginOwner,
    rearmContributions,
    reportContributionFailure,
    runContribution,
} from "./contribution-guard";

/**
 * The host's containment promise, at the level it is actually made: a subject's
 * contributed code throwing costs that one contribution, and the log names the
 * subject rather than the host frame it happened to throw in.
 */

const ORIGIN = {
    owner: pluginOwner("acme-plugin"),
    contribution: "symbol",
    phase: "match",
} as const;

const CONTENT = "contribution-content";
const SIBLING = "host-sibling";
const FALLBACK = "contribution-fallback";

let errors: unknown[][];

beforeEach(() => {
    __resetContributionFailureLog();
    errors = [];
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation((...args: unknown[]) => {
        errors.push(args);
    });
});

afterEach(() => {
    vi.restoreAllMocks();
    __resetContributionFailureLog();
});

describe("describeOrigin", () => {
    it.each([
        [pluginOwner("acme.heatmap"), "acme.heatmap:symbol:match"],
        [nativeOwner("opencop.shapes"), "native:opencop.shapes:symbol:match"],
        [undefined, "native:symbol:match"],
    ])("keys %o as %s", (owner, key) => {
        expect(describeOrigin({ owner, contribution: "symbol", phase: "match" })).toBe(key);
    });

    it("never gives a plugin and a native subject with the same id the same key", () => {
        const origin = { contribution: "symbol", phase: "match" };
        expect(describeOrigin({ ...origin, owner: pluginOwner("opencop.shapes") })).not.toBe(
            describeOrigin({ ...origin, owner: nativeOwner("opencop.shapes") }),
        );
    });
});

describe("runContribution", () => {
    it("returns the callback's value when it does not throw", () => {
        expect(
            runContribution(
                ORIGIN,
                () => "ok",
                () => "fallback",
            ),
        ).toBe("ok");
        expect(errors).toHaveLength(0);
    });

    it("substitutes the fallback when it throws, and does not rethrow", () => {
        expect(
            runContribution(
                ORIGIN,
                () => {
                    throw new Error("boom");
                },
                () => "fallback",
            ),
        ).toBe("fallback");
    });

    it("attributes the failure to the plugin and phase, not to the host", () => {
        runContribution(
            ORIGIN,
            () => {
                throw new Error("boom");
            },
            () => null,
        );

        expect(String(errors[0][0])).toContain("acme-plugin:symbol:match");
    });

    it("labels a native subject's contribution as native", () => {
        runContribution(
            { contribution: "symbol", phase: "mapLayer" },
            () => {
                throw new Error("boom");
            },
            () => null,
        );

        expect(String(errors[0][0])).toContain("native:symbol:mapLayer");
    });

    it("logs once for a repeatedly-throwing origin", () => {
        // The layer factory re-runs on every rebuild — every WS push, every
        // terrain toggle — so an always-throwing renderer would flood the console.
        for (let i = 0; i < 5; i++) {
            runContribution(
                ORIGIN,
                () => {
                    throw new Error("boom");
                },
                () => null,
            );
        }
        expect(errors).toHaveLength(1);
    });

    it("logs again after the origin recovers and fails once more", () => {
        const throwing = () => {
            throw new Error("boom");
        };
        runContribution(ORIGIN, throwing, () => null);
        runContribution(
            ORIGIN,
            () => "ok",
            () => null,
        ); // recovered
        runContribution(ORIGIN, throwing, () => null);

        expect(errors).toHaveLength(2);
    });

    it("keeps two origins' suppression independent", () => {
        const throwing = () => {
            throw new Error("boom");
        };
        runContribution(ORIGIN, throwing, () => null);
        runContribution({ ...ORIGIN, phase: "preview" }, throwing, () => null);

        expect(errors).toHaveLength(2);
    });

    it("shares its suppression with reportContributionFailure", () => {
        // The call sites that must do their own containment report through the
        // same channel, so a failure is not logged twice under two spellings.
        runContribution(
            ORIGIN,
            () => {
                throw new Error("boom");
            },
            () => null,
        );
        reportContributionFailure(ORIGIN, new Error("boom"));

        expect(errors).toHaveLength(1);
    });
});

function Boom(): never {
    throw new Error("render boom");
}

describe("ContributionBoundary", () => {
    it("renders the contribution when it does not throw", () => {
        render(
            <ContributionBoundary origin={ORIGIN}>
                <div>{CONTENT}</div>
            </ContributionBoundary>,
        );
        expect(screen.getByText(CONTENT)).toBeInTheDocument();
    });

    it("contains a throwing contribution without unwinding to the caller", () => {
        render(
            <div>
                <span>{SIBLING}</span>
                <ContributionBoundary origin={ORIGIN}>
                    <Boom />
                </ContributionBoundary>
            </div>,
        );

        // The surrounding tree — the toolbar, the map — survives.
        expect(screen.getByText(SIBLING)).toBeInTheDocument();
    });

    it("renders the declared fallback in the contribution's place", () => {
        render(
            <ContributionBoundary origin={ORIGIN} fallback={<span>{FALLBACK}</span>}>
                <Boom />
            </ContributionBoundary>,
        );
        expect(screen.getByText(FALLBACK)).toBeInTheDocument();
    });

    it("attributes the render failure to the plugin and phase", () => {
        render(
            <ContributionBoundary origin={{ ...ORIGIN, phase: "editor" }}>
                <Boom />
            </ContributionBoundary>,
        );
        expect(errors.map(String).join(" ")).toContain("acme-plugin:symbol:editor");
    });
});

/**
 * A repeatedly-failing contribution is dropped rather than re-asked. The point is
 * the discrimination: one malformed element among good ones must NOT disable a
 * working contribution, while a genuinely broken one must stop costing the host a
 * throw per element — `resolveSpec` runs per element over batches of tens of
 * thousands.
 */
describe("runContribution — consecutive-failure breaker", () => {
    const LIMIT = 10;
    const boom = () => {
        throw new Error("boom");
    };
    /** Counts real invocations, so "stopped calling it" is observable. */
    function counting<T>(impl: () => T) {
        let calls = 0;
        return {
            get calls() {
                return calls;
            },
            fn: () => {
                calls += 1;
                return impl();
            },
        };
    }

    it("keeps calling a contribution that fails intermittently", () => {
        const c = counting(() => "ok");
        // One throw in every three — a bad element among good ones. The streak
        // never builds, so the contribution is never dropped.
        for (let i = 0; i < 60; i++) {
            runContribution(ORIGIN, i % 3 === 0 ? boom : c.fn, () => null);
        }
        expect(c.calls).toBe(40);
    });

    it("stops calling a contribution that fails every time", () => {
        const c = counting(boom);
        for (let i = 0; i < 100; i++) runContribution(ORIGIN, c.fn, () => null);

        expect(c.calls).toBe(LIMIT);
    });

    it("keeps returning the fallback once tripped", () => {
        for (let i = 0; i < LIMIT; i++) runContribution(ORIGIN, boom, () => "fallback");

        expect(
            runContribution(
                ORIGIN,
                () => "live",
                () => "fallback",
            ),
        ).toBe("fallback");
    });

    it("logs a second, louder line naming the streak when it trips", () => {
        for (let i = 0; i < LIMIT; i++) runContribution(ORIGIN, boom, () => null);

        const trip = errors.map(String).find((e) => e.includes("times in a row"));
        expect(trip).toBeDefined();
        expect(trip).toContain("acme-plugin:symbol:match");
        // Once, not once per subsequent call.
        for (let i = 0; i < 20; i++) runContribution(ORIGIN, boom, () => null);
        expect(errors.map(String).filter((e) => e.includes("times in a row"))).toHaveLength(1);
    });

    it("a single success before the limit resets the streak entirely", () => {
        for (let i = 0; i < LIMIT - 1; i++) runContribution(ORIGIN, boom, () => null);
        runContribution(
            ORIGIN,
            () => "ok",
            () => null,
        );

        const c = counting(boom);
        for (let i = 0; i < LIMIT - 1; i++) runContribution(ORIGIN, c.fn, () => null);
        // Still under the limit after the reset, so still being called.
        expect(c.calls).toBe(LIMIT - 1);
    });

    it("trips each origin independently", () => {
        for (let i = 0; i < LIMIT; i++) runContribution(ORIGIN, boom, () => null);

        const other = counting(boom);
        runContribution({ ...ORIGIN, phase: "preview" }, other.fn, () => null);
        expect(other.calls).toBe(1);
    });

    it("re-arms when the contribution is replaced, so a fixed plugin recovers", () => {
        for (let i = 0; i < LIMIT; i++) runContribution(ORIGIN, boom, () => null);
        expect(
            runContribution(
                ORIGIN,
                () => "live",
                () => "fallback",
            ),
        ).toBe("fallback");

        rearmContributions(ORIGIN.contribution);

        expect(
            runContribution(
                ORIGIN,
                () => "live",
                () => "fallback",
            ),
        ).toBe("live");
    });

    it("re-arms every phase of one contribution, not just the tripped one", () => {
        const phases = ["match", "mapLayer", "preview"] as const;
        for (const phase of phases) {
            for (let i = 0; i < LIMIT; i++) {
                runContribution({ ...ORIGIN, phase }, boom, () => null);
            }
        }

        rearmContributions(ORIGIN.contribution);

        for (const phase of phases) {
            expect(
                runContribution(
                    { ...ORIGIN, phase },
                    () => "live",
                    () => "fallback",
                ),
            ).toBe("live");
        }
    });

    it("leaves an unrelated contribution's state alone when re-arming", () => {
        const other = { ...ORIGIN, contribution: "graphic" };
        for (let i = 0; i < LIMIT; i++) runContribution(other, boom, () => null);

        rearmContributions(ORIGIN.contribution);

        expect(
            runContribution(
                other,
                () => "live",
                () => "fallback",
            ),
        ).toBe("fallback");
    });
});
