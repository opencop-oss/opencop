// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createElement } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { MediaIcon, Symbol, type Element } from "@opencop/elements";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import { __resetToolRegistry, registerTool, type ToolDescriptor } from "./tool-registry";
import { editToolFor } from "./edit-tool";

/**
 * Which tool edits an element: the tool that produces its type, else the host's
 * generic edit. The cases mirror today's subjects: symbology's tool produces
 * `symbol`, and no tool produces `media-icon`.
 */

const sidcSymbol = () => Symbol.create({ position: [0, 0], sidc: "10031000001211000000" });
const mediaIcon = () =>
    MediaIcon.create({ position: [0, 0], href: "opencop:url:/v1/layers/abc/media/def#32x32" });

const symbolTool: ToolDescriptor = {
    id: "symbolTool",
    label: "symbol",
    icon: () => createElement("span"),
    produces: { store: ELEMENT_STORE_ID, type: "symbol" },
};

const disposers: Array<() => void> = [];
const register = (...items: Array<() => void>) => disposers.push(...items);
const editedBy = (element: Element) => editToolFor(element)?.id;

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    __resetToolRegistry();
});

describe("editToolFor", () => {
    it("gives a symbol symbology's tool and a media icon the generic edit", () => {
        register(registerTool(symbolTool));

        expect(editedBy(sidcSymbol())).toBe("symbolTool");
        expect(editedBy(mediaIcon())).toBeUndefined();
    });

    it("gives the generic edit when no tool produces the type", () => {
        expect(editedBy(sidcSymbol())).toBeUndefined();
    });
});
