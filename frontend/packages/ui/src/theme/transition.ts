// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

const defaultDuration: number = 0.1;
const defaultTimingFunction: string = "ease-out";

export function getDefaultTransition(
    properties: string[],
    extras?: { duration?: number; transition?: string },
) {
    const d: number = extras?.duration ? extras.duration : defaultDuration;
    const e: string = extras?.transition ? extras.transition : "";
    return {
        transition: properties.map((p) => `${p} ${d}s ${defaultTimingFunction}`).toString() + e,
    };
}
