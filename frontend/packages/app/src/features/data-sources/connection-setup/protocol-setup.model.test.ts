// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { ConnectionExtractor, MapperIngressRule } from "@opencop/api";
import {
    buildMapperOnlyRequest,
    ingressMapperChoice,
    isMapperOnly,
    supportedMapperOnlyAccessModes,
    type MapperOnlySelection,
} from "./protocol-setup.model";

describe("ingressMapperChoice", () => {
    it.each<[string, MapperIngressRule[], ReturnType<typeof ingressMapperChoice>]>([
        ["opens to any mapper for an any-mapper rule", [{ type: "ANY_MAPPER" }], { kind: "ANY" }],
        [
            "lets an open rule win over named ones",
            [{ type: "MAPPER", mapperId: "m1" }, { type: "ANY_MAPPER" }],
            { kind: "ANY" },
        ],
        [
            "offers only the named mappers, each once",
            [
                { type: "MAPPER", mapperId: "m1" },
                { type: "MAPPER", mapperId: "m2" },
                { type: "MAPPER", mapperId: "m1" },
            ],
            { kind: "ONLY", mapperIds: ["m1", "m2"] },
        ],
        ["offers no mapper when there are no rules", [], { kind: "ONLY", mapperIds: [] }],
    ])("%s", (_, rules, expected) => {
        expect(ingressMapperChoice(rules)).toEqual(expected);
    });
});

describe("isMapperOnly", () => {
    it.each([
        ["TCP", false],
        ["UDP", true],
        ["RTSP", true],
        ["KAFKA", true],
    ] as const)("treats %s as mapper-only: %s", (protocol, expected) => {
        expect(isMapperOnly(protocol)).toBe(expected);
    });
});

describe("supportedMapperOnlyAccessModes", () => {
    it.each([
        ["keeps Read when the integration reports it", ["R"], ["R"]],
        ["drops Write modes the request cannot carry", ["R", "W", "RW"], ["R"]],
        ["offers nothing when the integration reports only Write", ["W"], []],
    ] as const)("%s", (_, reported, expected) => {
        expect(supportedMapperOnlyAccessModes(reported)).toEqual(expected);
    });
});

describe("buildMapperOnlyRequest", () => {
    const extractor: ConnectionExtractor = {
        id: "extractor-udp",
        label: "UDP listener",
        fields: [
            { name: "host", label: "Host", required: true, kind: "TEXT" },
            { name: "note", label: "Note", required: false, kind: "TEXT" },
            {
                name: "cert",
                label: "Certificate",
                required: false,
                kind: "FILE",
                extensions: [".pem"],
                mediaTypes: [],
            },
        ],
    };
    const selection: MapperOnlySelection = {
        kind: "UDP",
        name: "UDP source",
        access: "R",
        integration: { id: "native", schemaVersion: 1 },
        extractor,
        values: { host: "udp.example.test", note: "", undeclared: "dropped" },
        files: {},
        mapperId: "m1",
    };

    it("sends the mapper and the declared, filled values, with no framing or egress", () => {
        expect(buildMapperOnlyRequest(selection)).toEqual({
            request: {
                kind: "UDP",
                name: "UDP source",
                access: "R",
                integration: { id: "native", schemaVersion: 1 },
                extractorId: "extractor-udp",
                values: { host: "udp.example.test" },
                mapperId: "m1",
            },
            files: {},
        });
    });

    it("splits chosen files out of the JSON request", () => {
        const cert = new File(["pem"], "cert.pem");
        const { request, files } = buildMapperOnlyRequest({
            ...selection,
            files: { cert: [cert] },
        });

        expect(files).toEqual({ cert: [cert] });
        expect(request.values).toEqual({ host: "udp.example.test" });
    });
});
