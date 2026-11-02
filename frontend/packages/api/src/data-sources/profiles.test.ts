// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expectTypeOf, it } from "vitest";
import type { MapperProfileRequest } from "./profiles";

describe("MapperProfileRequest", () => {
    it("excludes every server-owned field, so a write payload cannot set them", () => {
        expectTypeOf<
            "createdAt" extends keyof MapperProfileRequest ? true : false
        >().toEqualTypeOf<false>();
        expectTypeOf<
            "createdBy" extends keyof MapperProfileRequest ? true : false
        >().toEqualTypeOf<false>();
        expectTypeOf<
            "updatedAt" extends keyof MapperProfileRequest ? true : false
        >().toEqualTypeOf<false>();
        expectTypeOf<
            "updatedBy" extends keyof MapperProfileRequest ? true : false
        >().toEqualTypeOf<false>();
        expectTypeOf<
            "lastEventAt" extends keyof MapperProfileRequest ? true : false
        >().toEqualTypeOf<false>();
    });
});
