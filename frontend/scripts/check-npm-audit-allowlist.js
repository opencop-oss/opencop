#!/usr/bin/env node
// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0


import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const allowlistPath = resolve(__dirname, "check-npm-audit-allowlist.json");

const SEVERITY_ORDER = ["info", "low", "moderate", "high", "critical"];

const { level, omitDev } = parseArgs(process.argv.slice(2));

const minSeverityIndex = SEVERITY_ORDER.indexOf(level);
if (minSeverityIndex === -1) {
    throw new Error(`check-npm-audit-allowlist: unknown --audit-level "${level}"`);
}

const allowlist = JSON.parse(readFileSync(allowlistPath, "utf8"));
if (!Array.isArray(allowlist)) {
    throw new Error("scripts/check-npm-audit-allowlist.json must contain a JSON array.");
}
for (const entry of allowlist) {
    validateAllowlistEntry(entry);
}
const allowlistById = new Map(allowlist.map((entry) => [entry.id, entry]));

const auditArgs = ["audit", "--json"];
if (omitDev) {
    auditArgs.push("--omit=dev");
}

// npm audit exits 1 when it finds vulnerabilities — that's expected and
// handled below. Only a genuinely unparseable report is a hard failure.
const result = spawnSync("npm", auditArgs, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });

let report;
try {
    report = JSON.parse(result.stdout);
} catch {
    console.error("check-npm-audit-allowlist: could not parse `npm audit --json` output.");
    console.error(result.stdout);
    console.error(result.stderr);
    process.exit(1);
}

const vulnerabilities = report.vulnerabilities || {};
const usedAllowlistIds = new Set();
const suppressed = [];
const blocking = [];

for (const [name, vuln] of Object.entries(vulnerabilities)) {
    if (SEVERITY_ORDER.indexOf(vuln.severity) < minSeverityIndex) {
        continue;
    }

    const coverage = coverageFor(name, vulnerabilities, new Set());

    if (coverage.fullyAllowlisted) {
        for (const id of coverage.idsUsed) {
            usedAllowlistIds.add(id);
        }
        suppressed.push({ name, severity: vuln.severity, ids: [...coverage.idsUsed] });
    } else {
        blocking.push({ name, severity: vuln.severity, uncovered: coverage.uncoveredAdvisoryIds });
    }
}

printSummary({ level, omitDev, suppressed, blocking, allowlist, usedAllowlistIds });

if (blocking.length > 0) {
    console.error("\nnpm audit found vulnerabilities not covered by the allowlist:");
    for (const entry of blocking) {
        console.error(`  - ${entry.name} (${entry.severity}): ${[...new Set(entry.uncovered)].join(", ") || "no direct advisory id"}`);
    }
    process.exit(1);
}

console.log("\nnpm audit allowlist check passed.");

// Determines whether every advisory feeding into `name` (directly, or
// cascaded through other flagged packages via `via` string references) is
// covered by the allowlist. A package with even one uncovered advisory in
// its chain is not fully allowlisted.
function coverageFor(name, allVulnerabilities, visiting) {
    if (visiting.has(name)) {
        // Defensive: npm audit's dependency graph shouldn't cycle, but don't
        // hang if it somehow does.
        return { fullyAllowlisted: true, idsUsed: new Set(), uncoveredAdvisoryIds: [] };
    }
    visiting.add(name);

    const vuln = allVulnerabilities[name];
    const idsUsed = new Set();
    const uncoveredAdvisoryIds = [];
    let fullyAllowlisted = true;

    for (const item of vuln.via) {
        if (typeof item === "string") {
            const nested = coverageFor(item, allVulnerabilities, visiting);
            for (const id of nested.idsUsed) {
                idsUsed.add(id);
            }
            uncoveredAdvisoryIds.push(...nested.uncoveredAdvisoryIds);
            if (!nested.fullyAllowlisted) {
                fullyAllowlisted = false;
            }
            continue;
        }

        const id = advisoryIdFromUrl(item.url);
        if (id && allowlistById.has(id)) {
            idsUsed.add(id);
        } else {
            fullyAllowlisted = false;
            uncoveredAdvisoryIds.push(id || item.title || "unknown advisory");
        }
    }

    visiting.delete(name);
    return { fullyAllowlisted, idsUsed, uncoveredAdvisoryIds };
}

function advisoryIdFromUrl(url) {
    if (!url) {
        return undefined;
    }
    const match = url.match(/GHSA-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+/i);
    return match ? match[0] : undefined;
}

function parseArgs(argv) {
    let level = "low";
    let omitDev = false;

    for (const arg of argv) {
        if (arg.startsWith("--audit-level=")) {
            level = arg.slice("--audit-level=".length);
        } else if (arg === "--omit=dev") {
            omitDev = true;
        }
    }

    return { level, omitDev };
}

function validateAllowlistEntry(entry) {
    const requiredStringFields = ["id", "package", "reason", "reference", "addedAt"];
    const missingFields = requiredStringFields.filter(
        (field) => typeof entry[field] !== "string" || entry[field].trim() === "",
    );

    if (missingFields.length > 0) {
        throw new Error(
            `scripts/check-npm-audit-allowlist.json: entry ${JSON.stringify(entry)} missing ${missingFields.join(", ")}`,
        );
    }

    if (entry.revisitBy !== undefined && !Number.isFinite(Date.parse(entry.revisitBy))) {
        throw new Error(`scripts/check-npm-audit-allowlist.json: entry ${entry.id} has invalid revisitBy: ${entry.revisitBy}`);
    }
}

function printSummary({ level, omitDev, suppressed, blocking, allowlist, usedAllowlistIds }) {
    console.log(`npm audit allowlist check: severity >= ${level}${omitDev ? ", production dependencies only" : ""}`);

    if (suppressed.length > 0) {
        console.log("Allowlisted findings (documented, not blocking):");
        for (const entry of suppressed) {
            console.log(`  - ${entry.name} (${entry.severity}): ${entry.ids.join(", ")}`);
        }
    }

    if (blocking.length === 0 && suppressed.length === 0) {
        console.log("No vulnerabilities at or above the configured severity.");
    }

    const unused = allowlist.filter((entry) => !usedAllowlistIds.has(entry.id));
    if (unused.length > 0) {
        console.warn("\nWARN: these allowlist entries matched nothing in this audit run — safe to remove if no longer relevant:");
        for (const entry of unused) {
            console.warn(`  - ${entry.id} (${entry.package}): ${entry.reason}`);
        }
    }

    const overdue = allowlist.filter((entry) => entry.revisitBy && Date.parse(entry.revisitBy) < Date.now());
    if (overdue.length > 0) {
        console.warn("\nWARN: these allowlist entries are past their revisit date — re-check whether a real fix now exists:");
        for (const entry of overdue) {
            console.warn(`  - ${entry.id} (${entry.package}): revisit was due ${entry.revisitBy}`);
        }
    }
}
