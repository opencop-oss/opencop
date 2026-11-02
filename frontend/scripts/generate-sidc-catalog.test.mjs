// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {
    SIDC_ARCHIVE_SHA256,
    SIDC_ARCHIVE_URL,
    SIDC_UPSTREAM_COMMIT,
    assertArchiveHash,
    buildCatalog,
    renderGeneratedModule,
} from "./generate-sidc-catalog.mjs";

const SCRIPT = fileURLToPath(new URL("./generate-sidc-catalog.mjs", import.meta.url));

test("pins the authorized Esri archive", () => {
    assert.equal(SIDC_UPSTREAM_COMMIT, "094e7647f0bdd001e42fd2a73ba802c995af20aa");
    assert.equal(
        SIDC_ARCHIVE_URL,
        `https://github.com/Esri/joint-military-symbology-xml/archive/${SIDC_UPSTREAM_COMMIT}.tar.gz`,
    );
    assert.equal(
        SIDC_ARCHIVE_SHA256,
        "ada496e063f05fa495eda92ccfefcb6dbc0a83ffc21a310f40acbfbe43b5f13f",
    );
});

test("rejects an archive whose bytes do not match the pinned hash", () => {
    assert.throws(
        () => assertArchiveHash(Buffer.from("not the authorized archive")),
        /SIDC archive SHA-256 mismatch/,
    );
});

test("the CLI rejects unauthorized bytes before attempting extraction or output", () => {
    const directory = mkdtempSync(join(tmpdir(), "sidc-generator-test-"));
    const archive = join(directory, "invalid.tar.gz");
    const output = join(directory, "sidc.generated.ts");
    writeFileSync(archive, "not a tar archive");

    const result = spawnSync(process.execPath, [SCRIPT, "--archive", archive, "--output", output], {
        encoding: "utf8",
    });

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /SIDC archive SHA-256 mismatch/);
    assert.equal(existsSync(output), false);

    rmSync(directory, { recursive: true, force: true });
});

test("the same extracted input produces byte-identical ordered output", () => {
    const directory = mkdtempSync(join(tmpdir(), "sidc-generator-fixture-"));
    const instanceDirectory = join(directory, "instance");
    mkdirSync(instanceDirectory);

    writeFileSync(
        join(instanceDirectory, "Base.xml"),
        `<Library>
  <AmplifierGroups>
    <AmplifierGroup Label="Unknown"><AmplifierGroupCode>0</AmplifierGroupCode><Amplifiers><Amplifier Label="Unknown"><AmplifierCode>0</AmplifierCode></Amplifier></Amplifiers></AmplifierGroup>
    <AmplifierGroup Label="Echelon"><AmplifierGroupCode>1</AmplifierGroupCode><Amplifiers><Amplifier Label="Team/Crew"><AmplifierCode>1</AmplifierCode></Amplifier><Amplifier Label="Extension"><AmplifierCode>9</AmplifierCode></Amplifier></Amplifiers></AmplifierGroup>
  </AmplifierGroups>
</Library>`,
    );
    writeFileSync(
        join(instanceDirectory, "Land_Unit.xml"),
        `<SymbolSet Label="Land Units">
  <SymbolSetCode><DigitOne>1</DigitOne><DigitTwo>0</DigitTwo></SymbolSetCode>
  <Entities><Entity Label="Unspecified"><EntityCode><DigitOne>0</DigitOne><DigitTwo>0</DigitTwo></EntityCode></Entity></Entities>
  <SectorTwoModifiers><Modifier Label="Tactical"><ModifierCode><DigitOne>4</DigitOne><DigitTwo>6</DigitTwo></ModifierCode></Modifier></SectorTwoModifiers>
</SymbolSet>`,
    );
    writeFileSync(
        join(instanceDirectory, "Land_Equipment.xml"),
        `<SymbolSet Label="Land Equipment">
  <SymbolSetCode><DigitOne>1</DigitOne><DigitTwo>5</DigitTwo></SymbolSetCode>
  <Entities><Entity Label="Unspecified"><EntityCode><DigitOne>0</DigitOne><DigitTwo>0</DigitTwo></EntityCode></Entity></Entities>
</SymbolSet>`,
    );

    const firstCatalog = buildCatalog(instanceDirectory);
    const secondCatalog = buildCatalog(instanceDirectory);
    const firstOutput = renderGeneratedModule(firstCatalog);
    const secondOutput = renderGeneratedModule(secondCatalog);

    assert.equal(firstOutput, secondOutput);
    assert.deepEqual(Object.keys(firstCatalog.symbolSets), ["10", "15"]);
    assert.deepEqual(firstCatalog.symbolSets["10"].sectorTwoModifiers["46"], {
        label: "mono",
        localeKey: "mono",
    });
    assert.equal("sectorTwoModifiers" in firstCatalog.symbolSets["15"], false);
    assert.deepEqual(Object.keys(firstCatalog.amplifiers["1"].descriptors), ["1"]);

    rmSync(directory, { recursive: true, force: true });
});
