#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { XMLParser } from "fast-xml-parser";
import { format, resolveConfig } from "prettier";

export const SIDC_UPSTREAM_COMMIT = "094e7647f0bdd001e42fd2a73ba802c995af20aa";
export const SIDC_ARCHIVE_URL = `https://github.com/Esri/joint-military-symbology-xml/archive/${SIDC_UPSTREAM_COMMIT}.tar.gz`;
export const SIDC_ARCHIVE_SHA256 =
    "ada496e063f05fa495eda92ccfefcb6dbc0a83ffc21a310f40acbfbe43b5f13f";

const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const DEFAULT_OUTPUT = join(REPOSITORY_ROOT, "packages/symbology/src/sidc.generated.ts");
const ARCHIVE_DIRECTORY = `joint-military-symbology-xml-${SIDC_UPSTREAM_COMMIT}`;

const xmlParser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "@",
    parseTagValue: false,
    trimValues: true,
});

function sha256(bytes) {
    return createHash("sha256").update(bytes).digest("hex");
}

export function assertArchiveHash(bytes) {
    const actualSha256 = sha256(bytes);
    if (actualSha256 !== SIDC_ARCHIVE_SHA256) {
        throw new Error(
            `SIDC archive SHA-256 mismatch: expected ${SIDC_ARCHIVE_SHA256}, received ${actualSha256}`,
        );
    }
}

function asArray(value) {
    if (value === undefined) return [];
    return Array.isArray(value) ? value : [value];
}

function digits(code) {
    return `${code.DigitOne}${code.DigitTwo}`;
}

function labelToLocaleKey(label) {
    const words = label
        .replace(/[^a-zA-Z0-9\s]/g, " ")
        .trim()
        .split(/\s+/)
        .filter(Boolean);

    return words
        .map((word, index) =>
            index === 0
                ? word.toLowerCase()
                : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase(),
        )
        .join("");
}

function label(node) {
    const text = node["@Label"];
    return { label: text, localeKey: labelToLocaleKey(text) };
}

function codedLabel(node, codeProperty) {
    return { ...label(node), digits: digits(node[codeProperty]) };
}

function recordByDigits(entries) {
    return Object.fromEntries(
        entries.map(({ digits: entryDigits, ...entry }) => [entryDigits, entry]),
    );
}

function entityType(node) {
    return {
        ...codedLabel(node, "EntityTypeCode"),
        entitySubTypes: recordByDigits(
            asArray(node.EntitySubTypes?.EntitySubType).map((entry) =>
                codedLabel(entry, "EntitySubTypeCode"),
            ),
        ),
    };
}

function entity(node) {
    return {
        ...codedLabel(node, "EntityCode"),
        entityTypes: recordByDigits(asArray(node.EntityTypes?.EntityType).map(entityType)),
    };
}

function modifier(node) {
    return codedLabel(node, "ModifierCode");
}

function symbolSet(document) {
    const node = document.SymbolSet;
    const result = {
        ...label(node),
        digits: digits(node.SymbolSetCode),
        entities: recordByDigits(asArray(node.Entities?.Entity).map(entity)),
        sectorOneModifiers: recordByDigits(
            asArray(node.SectorOneModifiers?.Modifier).map(modifier),
        ),
        sectorTwoModifiers: recordByDigits(
            asArray(node.SectorTwoModifiers?.Modifier).map(modifier),
        ),
    };

    const specialEntitySubTypes = asArray(node.SpecialEntitySubTypes?.EntitySubType).map((entry) =>
        codedLabel(entry, "EntitySubTypeCode"),
    );
    if (specialEntitySubTypes.length > 0) {
        result.specialEntitySubTypes = recordByDigits(specialEntitySubTypes);
    }

    // Preserve the accepted OpenCOP catalog exactly. The historical catalog
    // intentionally labels Land Units sector-two modifier 46 as "mono" rather
    // than upstream "Tactical", and omits the unused empty sector-two map for
    // Land Equipment. Keeping these bounded deltas avoids a UI/data-shape change.
    if (result.digits === "10") {
        result.sectorTwoModifiers["46"] = { label: "mono", localeKey: "mono" };
    }
    if (result.digits === "15") {
        delete result.sectorTwoModifiers;
    }

    return result;
}

function amplifierGroup(node) {
    return {
        ...label(node),
        digits: node.AmplifierGroupCode,
        descriptors: recordByDigits(
            asArray(node.Amplifiers?.Amplifier)
                .map((entry) => ({
                    ...label(entry),
                    digits: entry.AmplifierCode,
                }))
                .filter((entry) => entry.digits !== "9"),
        ),
    };
}

export function buildCatalog(instanceDirectory) {
    const base = xmlParser.parse(readFileSync(join(instanceDirectory, "Base.xml"), "utf8"));
    const amplifiers = asArray(base.Library.AmplifierGroups.AmplifierGroup)
        .map(amplifierGroup)
        .filter((entry) => entry.digits !== "9");

    const symbolSets = readdirSync(instanceDirectory)
        .filter((name) => name.endsWith(".xml") && name !== "Base.xml")
        .sort()
        .map((name) => xmlParser.parse(readFileSync(join(instanceDirectory, name), "utf8")))
        .filter((document) => document.SymbolSet)
        .map(symbolSet)
        .filter((entry) => entry.digits !== "98")
        .sort((left, right) => left.digits.localeCompare(right.digits));

    return {
        amplifiers: recordByDigits(amplifiers),
        symbolSets: recordByDigits(symbolSets),
    };
}

export function renderGeneratedModule(catalog) {
    const source = `/**
 * @generated by scripts/generate-sidc-catalog.mjs — do not edit directly.
 *
 * Source: Esri joint-military-symbology-xml
 * Revision: ${SIDC_UPSTREAM_COMMIT}
 * Archive: ${SIDC_ARCHIVE_URL}
 * Archive SHA-256: ${SIDC_ARCHIVE_SHA256}
 * Upstream notice: Copyright 2014 Esri; licensed under Apache-2.0.
 *
 * OpenCOP modifications: retain only labels and SIDC digit relationships used by
 * the application; omit unused XML metadata and the internal symbol set; derive
 * stable locale keys from labels; preserve the documented OpenCOP 10/46
 * "mono" label and symbol-set-15 optional-field shape; emit deterministic
 * TypeScript lookup records.
 */

import type { SIDCAmplifier, SIDCSymbolSet } from "./sidc";

export const GENERATED_SIDC_AMPLIFIERS: Record<string, SIDCAmplifier> = ${JSON.stringify(catalog.amplifiers, null, 4)};

export const GENERATED_SIDC_SYMBOL_SETS: Record<string, SIDCSymbolSet> = ${JSON.stringify(catalog.symbolSets, null, 4)};
`;

    return source;
}

async function downloadArchive() {
    const response = await fetch(SIDC_ARCHIVE_URL, { redirect: "follow" });
    if (!response.ok) {
        throw new Error(`Failed to download SIDC archive: HTTP ${response.status}`);
    }
    return Buffer.from(await response.arrayBuffer());
}

function parseArguments(arguments_) {
    const options = {
        archive: undefined,
        output: DEFAULT_OUTPUT,
    };

    for (let index = 0; index < arguments_.length; index += 1) {
        const argument = arguments_[index];
        const value = arguments_[index + 1];
        if (argument === "--archive" && value) options.archive = resolve(value);
        else if (argument === "--output" && value) options.output = resolve(value);
        else throw new Error(`Unknown or incomplete argument: ${argument}`);
        index += 1;
    }

    return options;
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    const archiveBytes = options.archive ? readFileSync(options.archive) : await downloadArchive();

    // The verified bytes are copied to a private temporary path. Extraction never
    // sees unverified or subsequently replaced input bytes.
    assertArchiveHash(archiveBytes);

    const temporaryDirectory = mkdtempSync(join(tmpdir(), "opencop-sidc-"));
    try {
        const archive = join(temporaryDirectory, "verified.tar.gz");
        const extractionDirectory = join(temporaryDirectory, "extracted");
        writeFileSync(archive, archiveBytes);
        mkdirSync(extractionDirectory);
        execFileSync("tar", ["-xzf", archive, "-C", extractionDirectory], { stdio: "pipe" });

        const catalog = buildCatalog(join(extractionDirectory, ARCHIVE_DIRECTORY, "instance"));
        mkdirSync(dirname(options.output), { recursive: true });
        const prettierConfig = (await resolveConfig(options.output)) ?? {};
        const generatedSource = await format(renderGeneratedModule(catalog), {
            ...prettierConfig,
            parser: "typescript",
        });
        writeFileSync(options.output, generatedSource, "utf8");
        process.stdout.write(`Generated ${options.output}\n`);
    } finally {
        rmSync(temporaryDirectory, { recursive: true, force: true });
    }
}

const isMain = process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url;
if (isMain) {
    main().catch((error) => {
        process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
        process.exitCode = 1;
    });
}
