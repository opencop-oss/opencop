// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MediaIcon, Symbol } from "@opencop/elements";
import { ElementMessage } from "@opencop/proto";
import * as uuid from "uuid";
import { describe, expect, it } from "vitest";

import { getProcessor } from "./init-processor.util";

function message(type: string, values: ElementMessage["values"]): ElementMessage {
    return ElementMessage.create({
        type,
        id: uuid.parse("11111111-1111-4111-8111-111111111111"),
        objectId: "entity",
        source: uuid.parse("22222222-2222-4222-8222-222222222222"),
        displayName: "Entity",
        positions: [-77, 38],
        stale: 0,
        values,
    });
}

describe("getProcessor", () => {
    it("decodes a media-icon message into a media icon carrying its href", () => {
        const href = "opencop:url:/v1/layers/abc/media/def#48x24";

        const element = getProcessor().decode(message("media-icon", { href: { string: href } }));

        expect(element && MediaIcon.is(element)).toBe(true);
        if (!element || !MediaIcon.is(element)) throw new Error("Expected a media icon");
        expect(MediaIcon.getHref(element)).toBe(href);
        expect(element.getCode()).toBeUndefined();
        expect(element.geometry.getPosition()).toEqual([-77, 38]);
    });

    it("still decodes a symbol message into a symbol carrying its code", () => {
        const element = getProcessor().decode(
            message("symbol", { symbol: { string: "10031000001211000000" } }),
        );

        expect(element && Symbol.is(element)).toBe(true);
        expect(element?.getCode()).toBe("10031000001211000000");
    });
});
