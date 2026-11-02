// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { ElementMessage } from "@opencop/proto";
import { TextBoxMapper } from "./TextBoxMapper";

describe("TextBoxMapper", () => {
    it.each([
        ["east", [0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01]],
        ["west", [0.02, 0.01, 0, 0.01, 0, 0, 0.02, 0]],
    ])("keeps a ring whose first edge runs %s as stored", (_edge, positions) => {
        const message = ElementMessage.create({ positions });

        const element = new TextBoxMapper().fromMessage(message, {});

        expect(element.geometry.getAllPos()).toEqual(positions);
    });
});
