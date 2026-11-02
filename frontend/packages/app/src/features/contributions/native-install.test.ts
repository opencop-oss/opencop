// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import type { ContributionContext, ElementMapper } from "@opencop/contributions";
import {
    getNativeSubjectRecords,
    installNativeSubjects,
    type NativeSubject,
} from "./native-install";

/**
 * The native install transaction against fake sinks: what each subject leaves in
 * the "registries", what it is recorded as, and what a refusal costs. The real
 * root and registries are covered by `install-built-in-subjects.test.ts`.
 */

const REFUSED = "refused-type";

/** What the fake registries hold, as `<subject>/<type>`. */
let registered: string[] = [];
/** How many times a fake registry disposer has run. */
let disposals = 0;

function contextFor(subjectId: string): ContributionContext {
    const add = (entry: string) => {
        registered.push(entry);
        return () => {
            disposals++;
            registered = registered.filter((e) => e !== entry);
        };
    };
    return {
        registerTool: (tool) => add(`${subjectId}/tool:${tool.id}`),
        registerElementMapper: (mapper) => {
            if (mapper.type === REFUSED) throw new Error(`mapper "${REFUSED}" refused`);
            return add(`${subjectId}/mapper:${mapper.type}`);
        },
        registerPickResponse: (id) => add(`${subjectId}/pick:${id}`),
        registerDetailsHeader: () => add(`${subjectId}/details-header`),
        registerDetailsPosition: () => add(`${subjectId}/details-position`),
        registerDetailsEditForm: (form) => add(`${subjectId}/details-edit-form:${form.id}`),
        registerDetailsInfoSection: (section) =>
            add(`${subjectId}/details-info-section:${section.id}`),
        registerFilterField: (field) => add(`${subjectId}/filter-field:${field.id}`),
        registerContextMenuItem: (item) => add(`${subjectId}/context-menu:${item.id}`),
    };
}

const mapper = (type: string): ElementMapper => ({ type, map: () => [] });

const subject = (id: string, install: NativeSubject["install"]): NativeSubject => ({ id, install });

const registersTwo = (id: string, second = "b") =>
    subject(id, (ctx) => {
        ctx.registerElementMapper(mapper("a"));
        ctx.registerElementMapper(mapper(second));
    });

const disposers: Array<() => void> = [];
const install = (subjects: readonly NativeSubject[]) => {
    const dispose = installNativeSubjects(subjects, contextFor);
    disposers.push(dispose);
    return dispose;
};

beforeEach(() => {
    registered = [];
    disposals = 0;
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
});

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
});

describe("installNativeSubjects", () => {
    it("installs every subject and records each active", () => {
        install([registersTwo("opencop.one"), registersTwo("opencop.two")]);

        expect(registered).toEqual([
            "opencop.one/mapper:a",
            "opencop.one/mapper:b",
            "opencop.two/mapper:a",
            "opencop.two/mapper:b",
        ]);
        expect(getNativeSubjectRecords()).toEqual([
            { id: "opencop.one", status: "active" },
            { id: "opencop.two", status: "active" },
        ]);
    });

    it("fails a subject refused on its second registration: it leaves nothing, the next installs", () => {
        install([registersTwo("opencop.broken", REFUSED), registersTwo("opencop.next")]);

        expect(registered).toEqual(["opencop.next/mapper:a", "opencop.next/mapper:b"]);
        expect(getNativeSubjectRecords()).toEqual([
            {
                id: "opencop.broken",
                status: "failed",
                error: `Error: mapper "${REFUSED}" refused`,
            },
            { id: "opencop.next", status: "active" },
        ]);
    });

    it("fails a subject whose install throws, and logs it once, attributed to the subject", () => {
        install([
            subject("opencop.throws", (ctx) => {
                ctx.registerElementMapper(mapper("a"));
                throw new Error("boom");
            }),
        ]);

        expect(registered).toEqual([]);
        expect(getNativeSubjectRecords()).toEqual([
            {
                id: "opencop.throws",
                status: "failed",
                error: "subject threw during install: Error: boom",
            },
        ]);
        expect(Logger.get("plugins").error).toHaveBeenCalledTimes(1);
        expect(vi.mocked(Logger.get("plugins").error).mock.calls[0]?.[0]).toContain(
            '"opencop.throws"',
        );
    });

    it("runs the disposer a failed subject returns at once, since nothing will journal it", () => {
        const sideEffect = vi.fn();
        install([
            subject("opencop.swallows", (ctx) => {
                try {
                    ctx.registerElementMapper(mapper(REFUSED));
                } catch {
                    // A subject that catches its own refusal still fails.
                }
                return sideEffect;
            }),
        ]);

        expect(sideEffect).toHaveBeenCalledTimes(1);
        expect(getNativeSubjectRecords()[0]?.status).toBe("failed");
    });

    describe("after install returns (a flag turning a feature on)", () => {
        function lateSubject(id: string) {
            let later: ((type: string) => () => void) | undefined;
            const s = subject(id, (ctx) => {
                ctx.registerElementMapper(mapper("a"));
                later = (type) => ctx.registerElementMapper(mapper(type));
            });
            return { subject: s, later: (type: string) => later!(type) };
        }

        it("journals a late registration, so dispose removes it", () => {
            const late = lateSubject("opencop.late");
            const dispose = install([late.subject]);

            late.later("b");
            expect(registered).toEqual(["opencop.late/mapper:a", "opencop.late/mapper:b"]);

            dispose();
            expect(registered).toEqual([]);
        });

        it("fails the subject on a late refusal without throwing into the caller", () => {
            const late = lateSubject("opencop.late");
            install([late.subject, registersTwo("opencop.other")]);

            expect(() => late.later(REFUSED)).not.toThrow();
            expect(registered).toEqual(["opencop.other/mapper:a", "opencop.other/mapper:b"]);
            expect(getNativeSubjectRecords()[0]).toMatchObject({
                id: "opencop.late",
                status: "failed",
            });
        });

        it("makes a failed subject's context inert", () => {
            const late = lateSubject("opencop.late");
            install([late.subject]);

            late.later(REFUSED);
            late.later("c");
            expect(registered).toEqual([]);
        });
    });

    it("does not re-run a disposer the subject already ran itself", () => {
        let undo: (() => void) | undefined;
        const dispose = install([
            subject("opencop.own", (ctx) => {
                undo = ctx.registerElementMapper(mapper("a"));
            }),
        ]);

        undo!();
        undo!();
        dispose();
        expect(disposals).toBe(1);
        expect(registered).toEqual([]);
    });

    it("disposes subjects in reverse, journals the returned disposer, and clears the records", () => {
        const order: string[] = [];
        const dispose = install([
            subject("opencop.one", () => () => order.push("one")),
            subject("opencop.two", () => () => order.push("two")),
        ]);

        dispose();
        expect(order).toEqual(["two", "one"]);
        expect(getNativeSubjectRecords()).toEqual([]);
    });

    it("installs again after dispose, with the same result", () => {
        const subjects = [registersTwo("opencop.one"), registersTwo("opencop.broken", REFUSED)];
        install(subjects)();
        expect(registered).toEqual([]);

        install(subjects);
        expect(registered).toEqual(["opencop.one/mapper:a", "opencop.one/mapper:b"]);
        expect(getNativeSubjectRecords().map(({ status }) => status)).toEqual(["active", "failed"]);
    });
});
