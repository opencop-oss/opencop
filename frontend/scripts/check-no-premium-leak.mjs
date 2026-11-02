#!/usr/bin/env node
// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Scans the host's built dist/ for premium code markers. Premium plugins load at
// runtime and must never appear in the OSS bundle; each is a separate build
// target the host `vite build` excludes.
//
// **The marker list is empty, so today this proves nothing about the bundle.** No
// premium package exists yet, and a marker is a string a real premium package
// ships. Until one is registered here, a green run means "nothing was looked
// for", not "the bundle is clean" — the CI job that runs it exists so the wiring
// is in place and calibrated ahead of the first premium package, and
// `check-no-premium-leak.test.mjs` pins that a registered marker really does
// fail the build.
//
// This is a build/CI gate, deliberately kept out of any shipped package so the
// premium-free runtime (e.g. @opencop/contributions/host) never carries premium concepts.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

/**
 * Sentinels a premium package ships. Register one per premium package when it
 * lands; an empty list is the pre-premium state, not a passing bundle scan.
 * @type {string[]}
 */
export const PREMIUM_MARKERS = [];

function jsFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) out.push(...jsFiles(full));
        else if (full.endsWith(".js")) out.push(full);
    }
    return out;
}

/**
 * Every `<marker> → <file>` pair found under `dist`. Exported so the calibration
 * test can drive it over a fixture directory with a real sentinel in it — the
 * production marker list being empty, nothing else could exercise a hit.
 *
 * @param {string} dist directory to scan
 * @param {string[]} markers sentinels to look for
 * @returns {string[]} offenders, empty when clean
 */
export function findPremiumLeaks(dist, markers) {
    if (markers.length === 0) return [];
    const offenders = [];
    for (const file of jsFiles(dist)) {
        const text = readFileSync(file, "utf8");
        for (const marker of markers) {
            if (text.includes(marker)) offenders.push(`${marker} → ${file}`);
        }
    }
    return offenders;
}

/**
 * @param {string[]} argv
 * @returns {{ dist: string, markers: string[] }}
 */
function parseArgs(argv) {
    let dist = join(REPO_ROOT, "dist");
    // Extra sentinels on top of PREMIUM_MARKERS. The calibration test uses this
    // to prove a hit fails the process; production passes no flags.
    const markers = [...PREMIUM_MARKERS];
    for (const arg of argv) {
        if (arg.startsWith("--dist=")) dist = resolve(arg.slice("--dist=".length));
        else if (arg.startsWith("--marker=")) markers.push(arg.slice("--marker=".length));
    }
    return { dist, markers };
}

/** @param {string[]} argv @returns {number} process exit code */
export function main(argv) {
    const { dist, markers } = parseArgs(argv);

    let distStat;
    try {
        distStat = statSync(dist);
    } catch {
        console.error(`no-premium-leak: no dist/ at ${dist} — run \`npm run vite:build\` first.`);
        return 2;
    }
    if (!distStat.isDirectory()) {
        console.error(`no-premium-leak: ${dist} is not a directory.`);
        return 2;
    }

    const offenders = findPremiumLeaks(dist, markers);
    if (offenders.length > 0) {
        console.error("no-premium-leak: FAIL — premium code found in the OSS bundle:");
        for (const line of offenders) console.error(`  ${line}`);
        return 1;
    }

    if (markers.length === 0) {
        console.log(
            `no-premium-leak: no markers registered — nothing was checked in ${dist}. ` +
                "This is the expected pre-premium state; it is NOT evidence the bundle is clean.",
        );
        return 0;
    }

    console.log(`no-premium-leak: OK (${markers.length} marker(s) checked in ${dist}).`);
    return 0;
}

// CLI only when invoked directly, so the test can import the pieces above.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    process.exit(main(process.argv.slice(2)));
}
