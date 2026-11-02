// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import fs from "node:fs";
import { describe, expect, it } from "vitest";

describe("SIDC generator public validation contract", () => {
    it("provides and documents standalone generator validation", () => {
        const packageJson = JSON.parse(fs.readFileSync("package.json", "utf8")) as {
            scripts: Record<string, string>;
        };
        const readme = fs.readFileSync("README.md", "utf8");
        const validationSection = readme.slice(
            readme.indexOf("## Validation"),
            readme.indexOf("## Publication boundary"),
        );

        expect(packageJson.scripts["test:sidc-generator"]).toBe(
            "node --test scripts/generate-sidc-catalog.test.mjs",
        );
        expect(validationSection).toContain("npm run test:sidc-generator");
    });
});
