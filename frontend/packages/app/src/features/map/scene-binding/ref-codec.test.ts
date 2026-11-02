// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { codec, type PrimitiveRef } from "./ref-codec";

const ref = (type: string, key: string, nanoid: string): PrimitiveRef => ({
    object: { type, key },
    nanoid,
});

describe("codec", () => {
    it("encodes a ref as type, key and nanoid, and decodes it back", () => {
        const primitive = ref("element", "source\0object", "label");

        const id = codec.encode(primitive);

        expect(id).toBe("element:source\0object:label");
        expect(codec.decode(id)).toEqual(primitive);
    });

    it("round-trips keys that contain colons", () => {
        const historical = ref("element", "source\0objectId:3", "path");
        const nested = ref("watchboxes", "a:b:c", "outline");

        expect(codec.decode(codec.encode(historical))).toEqual(historical);
        expect(codec.decode(codec.encode(nested))).toEqual(nested);
    });

    it("round-trips nanoids that contain colons", () => {
        const plugin = ref("element", "source\0objectId:3", "ring:0");

        expect(codec.decode(codec.encode(plugin))).toEqual(plugin);
    });

    it("round-trips plugin-scoped store types, which contain colons", () => {
        const scoped = ref("my-plugin:region", "k", "outline");

        expect(codec.decode(codec.encode(scoped))).toEqual(scoped);
    });

    it("decodes ids without a type, key and nanoid to undefined", () => {
        expect(codec.decode("")).toBeUndefined();
        expect(codec.decode("element")).toBeUndefined();
        expect(codec.decode("element:key")).toBeUndefined();
    });

    it("decodes malformed escapes to undefined", () => {
        expect(codec.decode("a:b:%")).toBeUndefined();
        expect(codec.decode("%:b:c")).toBeUndefined();
    });
});
