// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { circleGrammar, polygonGrammar, rectangleGrammar, traceGrammar } from "./shape-grammars";
import { circleEditModel } from "./circle.model";
import { kits } from "./kits";
import { rectangleEditModel } from "./rectangle.model";
import type { ToolDescriptor } from "./tool";

const bare: ToolDescriptor = { id: "t", label: "t", icon: () => null };

describe("kits", () => {
    it("bundle each built-in grammar with the edit model its shape needs", () => {
        expect(kits.polygon).toEqual({ grammar: polygonGrammar });
        expect(kits.trace).toEqual({ grammar: traceGrammar });
        expect(kits.circle).toEqual({ grammar: circleGrammar, editModel: circleEditModel });
        expect(kits.rectangle).toEqual({
            grammar: rectangleGrammar,
            editModel: rectangleEditModel,
        });
    });

    it("spread into a tool's draw kit, and a field named after the spread replaces that part", () => {
        const hints = () => [];
        const descriptor: ToolDescriptor = { ...bare, draw: { ...kits.circle, hints } };

        expect(descriptor.draw?.grammar).toBe(circleGrammar);
        expect(descriptor.draw?.editModel).toBe(circleEditModel);
        expect(descriptor.draw?.hints).toBe(hints);
    });
});
