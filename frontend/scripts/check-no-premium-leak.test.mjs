// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Calibration for scripts/check-no-premium-leak.mjs.
// Run: node --test scripts/check-no-premium-leak.test.mjs
//
// The production marker list is empty, so a green `npm run check:no-leak` says
// nothing about the bundle. These tests are what make the gate meaningful before
// the first premium package exists: they prove that WHEN a marker is registered,
// a bundle containing it fails the process — and that a clean bundle does not.
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { PREMIUM_MARKERS, findPremiumLeaks } from "./check-no-premium-leak.mjs";

const CHECK = fileURLToPath(new URL("./check-no-premium-leak.mjs", import.meta.url));
const SENTINEL = "__OPENCOP_PREMIUM_SENTINEL__";

/** A throwaway dist/ with one nested chunk containing `body`. */
function fixtureDist(body) {
    const dir = mkdtempSync(join(tmpdir(), "no-leak-"));
    mkdirSync(join(dir, "assets"), { recursive: true });
    writeFileSync(join(dir, "assets", "chunk-abc123.js"), body, "utf8");
    return dir;
}

function runCli(args) {
    return spawnSync("node", [CHECK, ...args], { encoding: "utf8" });
}

test("a registered marker present in the bundle fails the process", () => {
    const dist = fixtureDist(`export const x = "${SENTINEL}";\n`);
    try {
        const r = runCli([`--dist=${dist}`, `--marker=${SENTINEL}`]);
        assert.equal(r.status, 1, "a leaked sentinel must exit non-zero");
        assert.match(r.stderr, /premium code found in the OSS bundle/);
        assert.match(r.stderr, new RegExp(SENTINEL));
    } finally {
        rmSync(dist, { recursive: true, force: true });
    }
});

test("the same marker absent from the bundle passes", () => {
    const dist = fixtureDist('export const x = "ordinary chunk";\n');
    try {
        const r = runCli([`--dist=${dist}`, `--marker=${SENTINEL}`]);
        assert.equal(r.status, 0);
        assert.match(r.stdout, /1 marker\(s\) checked/);
    } finally {
        rmSync(dist, { recursive: true, force: true });
    }
});

test("nested chunks are scanned, not just the dist root", () => {
    const dist = fixtureDist(`const y = "${SENTINEL}";\n`);
    try {
        assert.equal(findPremiumLeaks(dist, [SENTINEL]).length, 1);
    } finally {
        rmSync(dist, { recursive: true, force: true });
    }
});

test("non-.js files are not scanned", () => {
    const dist = mkdtempSync(join(tmpdir(), "no-leak-"));
    try {
        writeFileSync(join(dist, "index.html"), `<!-- ${SENTINEL} -->`, "utf8");
        assert.deepEqual(findPremiumLeaks(dist, [SENTINEL]), []);
    } finally {
        rmSync(dist, { recursive: true, force: true });
    }
});

test("a missing dist/ is a setup error (exit 2), not a pass", () => {
    const r = runCli([`--dist=${join(tmpdir(), "no-leak-does-not-exist")}`]);
    assert.equal(r.status, 2);
    assert.match(r.stderr, /run `npm run vite:build` first/);
});

test("an empty marker list reports that nothing was checked", () => {
    // Pins the current pre-premium state AND its honest wording. When the first
    // premium package registers a marker, this test is the reminder to update
    // the docs that describe the gate as passing trivially.
    assert.deepEqual(PREMIUM_MARKERS, []);

    const dist = fixtureDist("export const x = 1;\n");
    try {
        const r = runCli([`--dist=${dist}`]);
        assert.equal(r.status, 0);
        assert.match(r.stdout, /no markers registered — nothing was checked/);
        assert.doesNotMatch(r.stdout, /OK \(/);
    } finally {
        rmSync(dist, { recursive: true, force: true });
    }
});
