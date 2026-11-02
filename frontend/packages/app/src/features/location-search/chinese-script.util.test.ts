// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { toTraditionalDisplay } from "./chinese-script.util";

describe("toTraditionalDisplay", () => {
    it("converts the Simplified names Monaco's data carries", async () => {
        await expect(toTraditionalDisplay("蒙特卡洛大赌场")).resolves.toBe("蒙特卡洛大賭場");
        await expect(toTraditionalDisplay("摩纳哥")).resolves.toBe("摩納哥");
    });

    it("leaves text without Han characters untouched", async () => {
        await expect(toTraditionalDisplay("Place du Casino, Monaco")).resolves.toBe(
            "Place du Casino, Monaco",
        );
    });
});
