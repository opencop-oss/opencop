// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { createJournal } from "./journal.ts";

describe("createJournal", () => {
    it("rolls back newest first", () => {
        const journal = createJournal();
        const order: string[] = [];
        journal.add(() => order.push("a"));
        journal.add(() => order.push("b"));
        journal.add(() => order.push("c"));

        expect(journal.rollback()).toEqual([]);
        expect(order).toEqual(["c", "b", "a"]);
    });

    it("contains a throwing disposer, runs the rest, and returns the error", () => {
        const journal = createJournal();
        const order: string[] = [];
        const refused = new Error("refused");
        journal.add(() => order.push("a"));
        journal.add(() => {
            throw refused;
        });
        journal.add(() => order.push("c"));

        expect(journal.rollback()).toEqual([refused]);
        expect(order).toEqual(["c", "a"]);
    });

    it("is idempotent: a second rollback runs nothing", () => {
        const journal = createJournal();
        let runs = 0;
        journal.add(() => runs++);

        journal.rollback();
        journal.rollback();
        expect(runs).toBe(1);
    });

    it("hands back a once-only disposer that leaves the journal when the owner calls it", () => {
        const journal = createJournal();
        let runs = 0;
        const dispose = journal.add(() => runs++);

        dispose();
        dispose();
        journal.rollback();
        expect(runs).toBe(1);
    });

    it("does not run a disposer twice when it throws on the owner's own call", () => {
        const journal = createJournal();
        let runs = 0;
        const dispose = journal.add(() => {
            runs++;
            throw new Error("refused");
        });

        expect(dispose).toThrow("refused");
        expect(journal.rollback()).toEqual([]);
        expect(runs).toBe(1);
    });
});
