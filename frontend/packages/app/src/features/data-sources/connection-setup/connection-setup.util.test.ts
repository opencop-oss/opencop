// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { ExtractorField, IntegrationFraming, TcpFramingSelection } from "@opencop/api";
import { i18n } from "~/i18n/config";
import { describeFraming, groupFieldRows, isValidPort } from "./connection-setup.util";

describe("describeFraming", () => {
    const t = i18n.getFixedT("en");
    const COT: IntegrationFraming = { id: "cot", mode: "delimiterInclude" };
    const OTHER: IntegrationFraming = { id: "other", mode: "passthrough" };

    it.each<[string, TcpFramingSelection, string]>([
        [
            "a custom framing's mode and its delimiter as typed",
            { type: "CUSTOM", mode: "delimiterSplit", delimiter: "</event>\n" },
            "Delimiter Split (</event>\\n)",
        ],
        [
            "a custom framing with no delimiter by its mode alone",
            { type: "CUSTOM", mode: "passthrough" },
            "Pass-through",
        ],
    ])("shows %s", (_, framing, expected) => {
        expect(describeFraming(framing, undefined, undefined, t)).toBe(expected);
    });

    it("names an integration framing's provider and the mode it declares for the id", () => {
        expect(
            describeFraming({ type: "INTEGRATION", framingId: "cot" }, "TAK", [OTHER, COT], t),
        ).toBe("Provided by TAK (Delimiter Include)");
    });

    it.each<[string, readonly IntegrationFraming[] | undefined]>([
        ["the declarations aren't known", undefined],
        ["no declaration has its id", [OTHER]],
    ])("names the provider alone when %s", (_, declared) => {
        expect(describeFraming({ type: "INTEGRATION", framingId: "cot" }, "TAK", declared, t)).toBe(
            "Provided by TAK",
        );
    });

    it("has no text for an integration framing with no provider to name", () => {
        expect(
            describeFraming({ type: "INTEGRATION", framingId: "cot" }, undefined, [COT], t),
        ).toBeUndefined();
    });
});

const host: ExtractorField = {
    name: "host",
    label: "Host",
    required: true,
    kind: "TEXT",
    standardKey: "HOST",
};
const port: ExtractorField = {
    name: "port",
    label: "Port",
    required: true,
    kind: "TEXT",
    standardKey: "PORT",
};
const keyStore: ExtractorField = {
    name: "keyStore",
    label: "Key store",
    required: true,
    kind: "FILE",
    standardKey: "CLIENT_CERTIFICATE_STORE",
};

const clientPassword: ExtractorField = {
    name: "clientPassword",
    label: "Client password",
    required: true,
    kind: "SECRET",
    standardKey: "CLIENT_CERTIFICATE_PASSWORD",
};
const truststore: ExtractorField = {
    name: "truststore",
    label: "Truststore",
    required: false,
    kind: "FILE",
    standardKey: "TRUSTSTORE",
};
const truststorePassword: ExtractorField = {
    name: "truststorePassword",
    label: "Truststore password",
    required: false,
    kind: "SECRET",
    standardKey: "TRUSTSTORE_PASSWORD",
};

const names = (rows: ExtractorField[][]) => rows.map((row) => row.map((field) => field.name));

describe("groupFieldRows", () => {
    it("puts a host directly followed by a port on one row", () => {
        expect(names(groupFieldRows([host, port, keyStore]))).toEqual([
            ["host", "port"],
            ["keyStore"],
        ]);
    });

    it("keeps host and port apart when another field sits between them", () => {
        expect(names(groupFieldRows([host, keyStore, port]))).toEqual([
            ["host"],
            ["keyStore"],
            ["port"],
        ]);
    });

    it("pairs only in declaration order, never a port before a host", () => {
        expect(names(groupFieldRows([port, host]))).toEqual([["port"], ["host"]]);
    });

    it("puts each certificate store beside its own password", () => {
        expect(
            names(
                groupFieldRows([
                    host,
                    port,
                    keyStore,
                    clientPassword,
                    truststore,
                    truststorePassword,
                ]),
            ),
        ).toEqual([
            ["host", "port"],
            ["keyStore", "clientPassword"],
            ["truststore", "truststorePassword"],
        ]);
    });

    it("never pairs a store with the other store's password", () => {
        expect(names(groupFieldRows([keyStore, truststorePassword]))).toEqual([
            ["keyStore"],
            ["truststorePassword"],
        ]);
    });
});

describe("isValidPort", () => {
    it.each(["1", "8089", "65535", " 8080 ", ""])("accepts %j", (value) => {
        expect(isValidPort(value)).toBe(true);
    });

    it("leaves a missing value to the required rule", () => {
        expect(isValidPort(undefined)).toBe(true);
    });

    it.each(["0", "65536", "70000", "abc", "80a", "80.5", "-1", "8e3", "0x50"])(
        "rejects %j",
        (value) => {
            expect(isValidPort(value)).toBe(false);
        },
    );
});
