#!/usr/bin/env node
// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0


import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..");
const lockfilePath = resolve(repoRoot, "package-lock.json");
const allowlistPath = resolve(__dirname, "check-npm-lock-policy.allowlist.json");

const minReleaseAgeDays = Number.parseInt(process.env.NPM_LOCK_POLICY_MIN_AGE_DAYS || "7", 10);
const minReleaseAgeMs = minReleaseAgeDays * 24 * 60 * 60 * 1000;
const cutoff = new Date(Date.now() - minReleaseAgeMs);

const registryPrefixes = (process.env.NPM_LOCK_POLICY_REGISTRY || "https://registry.npmjs.org/")
    .split(",")
    .map((prefix) => prefix.trim())
    .filter(Boolean)
    .map((prefix) => (prefix.endsWith("/") ? prefix : `${prefix}/`));
const packumentRegistry = registryPrefixes[0];

const iocFragments = ["tanstack/setup", "router_init.js", "tanstack_runner"];
const iocPackages = new Set(["plain-crypto-js"]);
const iocVersions = new Set(["axios@1.14.1", "axios@0.30.4"]);

const lockfileRaw = readFileSync(lockfilePath, "utf8");
const lockfile = JSON.parse(lockfileRaw);
const packages = lockfile.packages || {};
const allowlist = JSON.parse(readFileSync(allowlistPath, "utf8"));

if (!Number.isFinite(minReleaseAgeDays) || minReleaseAgeDays < 0) {
    throw new Error(`Invalid NPM_LOCK_POLICY_MIN_AGE_DAYS value: ${process.env.NPM_LOCK_POLICY_MIN_AGE_DAYS}`);
}

if (!Array.isArray(allowlist)) {
    throw new Error("scripts/check-npm-lock-policy.allowlist.json must contain a JSON array.");
}

const errors = [];
const warnings = [];
const entriesToCheck = [];

const activeAllowlist = allowlist.filter((entry) => {
    const validation = validateAllowlistEntry(entry);

    if (!validation.valid) {
        warnings.push(`Ignoring invalid npm lockfile policy allowlist entry: ${validation.reason}`);
        return false;
    }

    const until = Date.parse(entry.until);
    return until > Date.now();
});

for (const fragment of iocFragments) {
    if (lockfileRaw.includes(fragment)) {
        errors.push(`Known npm incident IOC found in package-lock.json: ${fragment}`);
    }
}

for (const [path, entry] of Object.entries(packages)) {
    if (path === "") {
        continue;
    }

    if (entry.link === true || entry.inBundle === true) {
        continue;
    }

    const name = packageNameFor(path, entry);
    const version = entry.version;

    if (!name) {
        errors.push(`${path}: could not determine package name.`);
        continue;
    }

    if (!version) {
        errors.push(`${path}: missing package version.`);
        continue;
    }

    if (iocPackages.has(name) || iocVersions.has(`${name}@${version}`)) {
        errors.push(`${path}: known malicious package/version ${name}@${version} is not allowed.`);
    }

    if (!entry.resolved) {
        errors.push(`${path}: missing resolved URL.`);
        continue;
    }

    if (!registryPrefixes.some((prefix) => entry.resolved.startsWith(prefix))) {
        errors.push(`${path}: resolved URL is not an allowed npm registry tarball: ${entry.resolved}`);
        continue;
    }

    entriesToCheck.push({ path, name, version });
}

const versionsByName = new Map();
for (const entry of entriesToCheck) {
    if (!versionsByName.has(entry.name)) {
        versionsByName.set(entry.name, new Set());
    }
    versionsByName.get(entry.name).add(entry.version);
}

const publishTimesById = await fetchPublishTimes(versionsByName);

for (const entry of entriesToCheck) {
    const id = packageId(entry.name, entry.version);
    const publishedAt = publishTimesById.get(id);

    if (!publishedAt) {
        errors.push(`${entry.path}: registry metadata did not include publish time for ${id}.`);
        continue;
    }

    if (publishedAt > cutoff) {
        const allowlistEntry = activeAllowlist.find((candidate) => matchesAllowlist(candidate, entry, publishedAt));

        if (allowlistEntry) {
            warnings.push(
                `${entry.path}: allowed temporary exception for ${id}, published ${publishedAt.toISOString()}, until ${allowlistEntry.until}`,
            );
            continue;
        }

        errors.push(
            [
                `${entry.path}: ${id} was published too recently.`,
                `published=${publishedAt.toISOString()}`,
                `age=${formatAge(Date.now() - publishedAt.getTime())}`,
                `required>=${minReleaseAgeDays}d`,
            ].join(" "),
        );
    }
}

printSummary(entriesToCheck, versionsByName);

if (activeAllowlist.length > 0) {
    console.log("Active npm lockfile policy exceptions:");
    for (const entry of activeAllowlist) {
        const scope = entry.lockfilePath ? `${entry.name}@${entry.version} at ${entry.lockfilePath}` : `${entry.name}@${entry.version}`;
        console.log(`  - ${scope} until ${entry.until}: ${entry.reason || "no reason provided"}`);
    }
} else {
    console.log("Active npm lockfile policy exceptions: none");
}

for (const warning of warnings) {
    console.warn(`WARN: ${warning}`);
}

if (errors.length > 0) {
    console.error("\nNPM lockfile policy check failed:");
    for (const error of errors) {
        console.error(`  - ${error}`);
    }
    process.exit(1);
}

console.log("NPM lockfile policy check passed.");

function packageNameFor(path, entry) {
    if (entry.name) {
        return entry.name;
    }

    const marker = "node_modules/";
    const markerIndex = path.lastIndexOf(marker);
    if (markerIndex === -1) {
        return undefined;
    }

    const remaining = path.slice(markerIndex + marker.length).split("/");
    if (remaining[0]?.startsWith("@")) {
        return `${remaining[0]}/${remaining[1]}`;
    }

    return remaining[0];
}

async function fetchPublishTimes(packageVersions) {
    const publishTimes = new Map();
    const names = [...packageVersions.keys()].sort();
    // Default of 20 caused socket-pool exhaustion on CI runners; 5 is enough
    // to be fast while keeping the number of concurrent TLS connections low.
    const concurrency = Number.parseInt(process.env.NPM_LOCK_POLICY_FETCH_CONCURRENCY || "5", 10);
    let nextIndex = 0;

    async function worker() {
        while (nextIndex < names.length) {
            const name = names[nextIndex];
            nextIndex += 1;

            const packument = await fetchPackument(name);
            const versions = packageVersions.get(name);

            for (const version of versions) {
                const publishedAt = packument.time?.[version];
                if (publishedAt) {
                    publishTimes.set(packageId(name, version), new Date(publishedAt));
                }
            }
        }
    }

    await Promise.all(
        Array.from({ length: Math.min(concurrency, names.length) }, () => worker()),
    );

    return publishTimes;
}

async function fetchPackument(name) {
    const url = `${packumentRegistry}${encodePackageName(name)}`;
    const headers = { accept: "application/json" };
    let lastError;

    // 5 attempts with exponential backoff (500ms, 1s, 2s, 4s).
    // 3 attempts with linear 250ms backoff was not enough to survive the TLS
    // socket resets (UND_ERR_SOCKET) that CI runners encounter when the npm
    // registry closes the connection mid-stream under load.
    for (let attempt = 1; attempt <= 5; attempt += 1) {
        try {
            const response = await fetch(url, { headers });

            if (response.ok) {
                return response.json();
            }

            const body = await response.text();
            lastError = new Error(`HTTP ${response.status} fetching ${url}: ${body.slice(0, 200)}`);

            if (response.status < 500 && response.status !== 429) {
                break;
            }
        } catch (error) {
            lastError = error;
        }

        // Exponential backoff: give the registry (and the local socket pool)
        // progressively more time to recover before the next attempt.
        await sleep(500 * 2 ** (attempt - 1));
    }

    throw lastError;
}

function encodePackageName(name) {
    if (!name.startsWith("@")) {
        return encodeURIComponent(name);
    }

    const [scope, pkg] = name.split("/");
    return `${scope}%2f${pkg}`;
}

function matchesAllowlist(candidate, entry, publishedAt) {
    return (
        candidate.name === entry.name &&
        candidate.version === entry.version &&
        candidate.lockfilePath === entry.path &&
        Date.parse(candidate.publishedAt) === publishedAt.getTime()
    );
}

function packageId(name, version) {
    return `${name}@${version}`;
}

function formatAge(ageMs) {
    const days = ageMs / (24 * 60 * 60 * 1000);
    return `${days.toFixed(2)}d`;
}

function printSummary(entries, versionsByName) {
    const uniqueVersions = [...versionsByName.values()].reduce((count, versions) => count + versions.size, 0);

    console.log(`NPM lockfile policy: min release age ${minReleaseAgeDays}d, cutoff ${cutoff.toISOString()}`);
    console.log(`Allowed registry prefixes: ${registryPrefixes.join(", ")}`);
    console.log(`Checked ${entries.length} lockfile entries across ${versionsByName.size} packages and ${uniqueVersions} versions.`);
}

function sleep(ms) {
    return new Promise((resolvePromise) => {
        setTimeout(resolvePromise, ms);
    });
}

function validateAllowlistEntry(entry) {
    if (!entry || typeof entry !== "object") {
        return { valid: false, reason: "entry must be an object" };
    }

    const requiredStringFields = [
        "name",
        "version",
        "lockfilePath",
        "publishedAt",
        "reason",
        "approver",
        "merge_request",
        "until",
    ];
    const missingFields = requiredStringFields.filter(
        (field) => typeof entry[field] !== "string" || entry[field].trim() === "",
    );

    if (missingFields.length > 0) {
        return {
            valid: false,
            reason: `${entry.name || "<unknown>"}@${entry.version || "<unknown>"} missing ${missingFields.join(", ")}`,
        };
    }

    if (!Number.isFinite(Date.parse(entry.publishedAt))) {
        return { valid: false, reason: `${entry.name}@${entry.version} has invalid publishedAt: ${entry.publishedAt}` };
    }

    if (!Number.isFinite(Date.parse(entry.until))) {
        return { valid: false, reason: `${entry.name}@${entry.version} has invalid until: ${entry.until}` };
    }

    return { valid: true };
}
