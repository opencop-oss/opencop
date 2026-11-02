// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Vec from "./Vec";

export class VecF64 extends Vec<Float64Array> {
    constructor();
    constructor(capacity?: number);
    constructor(array: Float64Array);
    constructor(arg?: number | Float64Array) {
        super(Float64Array, arg);
    }
}
