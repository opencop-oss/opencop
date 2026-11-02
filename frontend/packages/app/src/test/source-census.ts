// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * A walk over the app's production source for the census tests — the
 * assertions that no input listener exists outside the interaction runtime's
 * arbitration module unless it is allowlisted with a reason. Shared by the
 * Escape and the pointer census (`features/map/interaction-runtime/arbitration/*-census.test.ts`).
 */
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

// Vitest runs with the package root as cwd (as `eslint.boundaries.test.ts`
// relies on too); `import.meta.url` is not a file URL under the DOM environment.
export const APP_SRC = join(process.cwd(), "packages/app/src");

/**
 * Every production `.ts` / `.tsx` file under `packages/app/src`, as paths
 * relative to it — skipping tests, stories, declarations, the `test/` folder,
 * and any directory in `exclude` (relative to `packages/app/src`).
 */
export function appSourceFiles(exclude: readonly string[]): string[] {
    const excluded = exclude.map((dir) => join(APP_SRC, dir));
    const out: string[] = [];
    const walk = (dir: string) => {
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
            const path = join(dir, entry.name);
            if (entry.isDirectory()) {
                if (entry.name === "test" || excluded.some((e) => path.startsWith(e))) continue;
                walk(path);
                continue;
            }
            if (!/\.(ts|tsx)$/.test(entry.name)) continue;
            if (/\.(test|stories|d)\.tsx?$/.test(entry.name)) continue;
            out.push(relative(APP_SRC, path));
        }
    };
    walk(APP_SRC);
    return out;
}

/** The file's source, roughly comment-free — good enough for a literal that no comment should hide behind. */
export function sourceWithoutComments(file: string): string {
    return readFileSync(join(APP_SRC, file), "utf8")
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/(^|[^:])\/\/.*$/gm, "$1");
}
