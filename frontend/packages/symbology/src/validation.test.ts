// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { validateSidc } from "./validation";
import { ACTION_POINT, FRIEND_INFANTRY, MAIN_ATTACK } from "./test-fixtures";

describe("point-symbol validation", () => {
    it.each(["", "123", "1234567890123456789A", "12345678901234567-89"])(
        "rejects malformed SIDC %s",
        (sidc) => {
            expect(validateSidc(sidc)).toEqual({ ok: false, kind: "invalid" });
        },
    );
    it("retains ordinary infantry symbols using the real renderer catalog", () => {
        expect(validateSidc(FRIEND_INFANTRY)).toEqual({ ok: true });
    });
    it.each([ACTION_POINT, MAIN_ATTACK, "99999999999999999999"])(
        "rejects excluded graphics or unknown SIDC %s",
        (sidc) => {
            expect(validateSidc(sidc)).toEqual({ ok: false, kind: "unsupported" });
        },
    );
});
