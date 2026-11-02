// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { AccessMode, ConnectionExtractor } from "@opencop/api";
import {
    buildTcpRequest,
    CUSTOM_FRAMING,
    egressMapperIds,
    escapeDelimiter,
    framingChoices,
    mapperChoice,
    NATIVE_INTEGRATION_ID,
    supportedTcpAccessModes,
    type TcpSelection,
    type TcpSetup,
    unescapeDelimiter,
} from "./tcp-setup.model";

const COT_FRAMING = { id: "framing-cot", mode: "delimiterInclude" as const };
const NEWLINE_FRAMING = { id: "framing-newline", mode: "delimiterSplit" as const };

function tcpSetup(ingress: TcpSetup["ingress"]): TcpSetup {
    return {
        kind: "TCP",
        accessModes: ["R"],
        extractors: [],
        ingress,
        egress: null,
        framings: [COT_FRAMING, NEWLINE_FRAMING],
    };
}

describe("framingChoices", () => {
    it("offers each integration framing once, then custom when a rule allows it", () => {
        const setup = tcpSetup([
            { type: "EXACT_PAIR", mapperId: "mapper-a", framingId: "framing-cot" },
            { type: "EXACT_PAIR", mapperId: "mapper-b", framingId: "framing-cot" },
            { type: "ANY_MAPPER_WITH", framingId: "framing-newline" },
            { type: "ANY_MAPPER_CUSTOM_FRAMING" },
        ]);

        expect(framingChoices(setup).map((choice) => choice.value)).toEqual([
            "framing-cot",
            "framing-newline",
            CUSTOM_FRAMING,
        ]);
    });

    it("skips a framing id that the setup does not resolve", () => {
        const setup = tcpSetup([{ type: "ANY_MAPPER_WITH", framingId: "framing-missing" }]);

        expect(framingChoices(setup)).toEqual([]);
    });
});

describe("mapperChoice", () => {
    const setup = tcpSetup([
        { type: "EXACT_PAIR", mapperId: "mapper-a", framingId: "framing-cot" },
        { type: "EXACT_PAIR", mapperId: "mapper-b", framingId: "framing-cot" },
        { type: "ANY_MAPPER_WITH", framingId: "framing-newline" },
        { type: "ANY_MAPPER_CUSTOM_FRAMING" },
    ]);

    it("limits an exact-pair framing to the mappers its pairs name", () => {
        expect(mapperChoice(setup, "framing-cot")).toEqual({
            kind: "ONLY",
            mapperIds: ["mapper-a", "mapper-b"],
        });
    });

    it("opens the mapper for an any-mapper framing and for custom framing", () => {
        expect(mapperChoice(setup, "framing-newline")).toEqual({ kind: "ANY" });
        expect(mapperChoice(setup, CUSTOM_FRAMING)).toEqual({ kind: "ANY" });
    });
});

describe("supportedTcpAccessModes", () => {
    const writable: TcpSetup = {
        ...tcpSetup([{ type: "ANY_MAPPER_CUSTOM_FRAMING" }]),
        accessModes: ["R", "W", "RW"],
        egress: { anyMapper: true, allowedMappers: [] },
    };

    it("keeps Native to Read even though its setup declares egress", () => {
        expect(supportedTcpAccessModes(writable, NATIVE_INTEGRATION_ID)).toEqual(["R"]);
    });

    it("follows the setup for every other integration", () => {
        expect(supportedTcpAccessModes(writable, "tak")).toEqual(["R", "W", "RW"]);
    });

    it("offers only what Core reports, even where the rules would allow more", () => {
        expect(supportedTcpAccessModes({ ...writable, accessModes: ["R"] }, "tak")).toEqual(["R"]);
        expect(supportedTcpAccessModes({ ...writable, accessModes: ["W"] }, "tak")).toEqual(["W"]);
    });

    it("lists modes in the order R, W, RW and drops any it does not know", () => {
        expect(
            supportedTcpAccessModes(
                { ...writable, accessModes: ["RW", "X" as AccessMode, "R"] },
                "tak",
            ),
        ).toEqual(["R", "RW"]);
    });
});

describe("egressMapperIds", () => {
    const egress = [
        {
            id: "egress-a",
            name: "A",
            description: "",
            dynamicMapper: {},
        },
        {
            id: "egress-b",
            name: "B",
            description: "",
            dynamicMapper: {},
        },
    ];

    it("lists every egress mapper for an open rule and only the named ones for a closed rule", () => {
        expect(egressMapperIds({ anyMapper: true, allowedMappers: [] }, egress)).toEqual([
            "egress-a",
            "egress-b",
        ]);
        expect(
            egressMapperIds({ anyMapper: false, allowedMappers: ["egress-cot"] }, egress),
        ).toEqual(["egress-cot"]);
        expect(egressMapperIds(null, egress)).toEqual([]);
    });
});

describe("buildTcpRequest", () => {
    const extractor: ConnectionExtractor = {
        id: "extractor-mtls",
        label: "TCP with mutual TLS",
        fields: [
            { name: "host", label: "Host", required: true, kind: "TEXT" },
            { name: "port", label: "Port", required: true, kind: "TEXT" },
            {
                name: "keyStore",
                label: "Key store",
                required: true,
                kind: "FILE",
                extensions: [".p12"],
                mediaTypes: [],
            },
            { name: "clientPassword", label: "Password", required: true, kind: "SECRET" },
            {
                name: "truststore",
                label: "Truststore",
                required: false,
                kind: "FILE",
                extensions: [".p12"],
                mediaTypes: [],
            },
            {
                name: "truststorePassword",
                label: "Truststore password",
                required: false,
                kind: "SECRET",
            },
        ],
    };
    const keyStore = new File(["store"], "client.p12");
    const selection: TcpSelection = {
        name: "TCP source",
        access: "R",
        integration: { id: "native", schemaVersion: 1 },
        extractor,
        values: {
            host: "tcp.example.test",
            port: "8089",
            clientPassword: "secret",
            truststorePassword: "",
            staleFromOtherExtractor: "leak",
        },
        files: { keyStore: [keyStore], truststore: [], staleFile: [new File(["x"], "x.zip")] },
        mapperId: "mapper-a",
        egressMapperId: "egress-a",
        framing: CUSTOM_FRAMING,
        customMode: "delimiterSplit",
        customDelimiter: "|",
    };

    it("sends only the extractor's declared, filled fields and its chosen files", () => {
        const { request, files } = buildTcpRequest(selection);

        expect(request).toEqual({
            kind: "TCP",
            name: "TCP source",
            access: "R",
            integration: { id: "native", schemaVersion: 1 },
            extractorId: "extractor-mtls",
            values: { host: "tcp.example.test", port: "8089", clientPassword: "secret" },
            mapperId: "mapper-a",
            framing: { type: "CUSTOM", mode: "delimiterSplit", delimiter: "|" },
        });
        expect(files).toEqual({ keyStore: [keyStore] });
    });

    it("sends only the egress mapper for Write and both mappers for Read & Write", () => {
        const write = buildTcpRequest({ ...selection, access: "W" }).request;
        expect(write).toMatchObject({ access: "W", egressMapperId: "egress-a" });
        expect(write).not.toHaveProperty("mapperId");

        expect(buildTcpRequest({ ...selection, access: "RW" }).request).toMatchObject({
            access: "RW",
            mapperId: "mapper-a",
            egressMapperId: "egress-a",
        });
    });

    it("omits the delimiter for a custom mode that takes none", () => {
        const { request } = buildTcpRequest({
            ...selection,
            customMode: "passthrough",
        });

        expect(request.framing).toEqual({ type: "CUSTOM", mode: "passthrough" });
    });

    it("omits the delimiter for binary, which Core rejects a delimiter for", () => {
        const { request } = buildTcpRequest({ ...selection, customMode: "binary" });

        expect(request.framing).toEqual({ type: "CUSTOM", mode: "binary" });
    });

    it("sends the delimiter's real characters, not the typed escape", () => {
        const { request } = buildTcpRequest({ ...selection, customDelimiter: "\\r\\n" });

        expect(request.framing).toEqual({
            type: "CUSTOM",
            mode: "delimiterSplit",
            delimiter: "\r\n",
        });
    });

    it("references an integration framing by id", () => {
        const { request } = buildTcpRequest({ ...selection, framing: "framing-cot" });

        expect(request.framing).toEqual({ type: "INTEGRATION", framingId: "framing-cot" });
    });
});

describe("unescapeDelimiter", () => {
    it.each([
        ["\\n", "\n"],
        ["\\t", "\t"],
        ["</event>\\n", "</event>\n"],
        ["\\\\n", "\\n"],
        ["|", "|"],
        ["\\x", "\\x"],
        ["\\\n", "\\\n"],
        ["end\\", "end\\"],
    ])("turns %j into %j", (typed, real) => {
        expect(unescapeDelimiter(typed)).toBe(real);
    });
});

describe("escapeDelimiter", () => {
    it.each([
        ["\n", "\\n"],
        ["\t", "\\t"],
        ["\r\n", "\\r\\n"],
        ["</event>\n", "</event>\\n"],
        ["\\n", "\\\\n"],
        ["|", "|"],
    ])("shows %j as %j, which types back to the same delimiter", (real, typed) => {
        expect(escapeDelimiter(real)).toBe(typed);
        expect(unescapeDelimiter(typed)).toBe(real);
    });
});
