// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import assertNever from "assert-never";
import type { StandardIdentityFrame } from "@opencop/symbology";

const svgProps = {
    fill: "none",
    stroke: "currentColor",
    strokeWidth: "1.5",
} as const;

/**
 * The bare affiliation frame for one standard identity, drawn in `currentColor`.
 *
 * Carries no colour, sizing, or `aria-hidden` of its own — callers supply those for their
 * own layout context.
 */
export function AffiliationFrameIcon({ frame }: { frame: StandardIdentityFrame }) {
    switch (frame) {
        case "circle":
            return (
                <svg viewBox="0 0 16 16" {...svgProps}>
                    <circle cx="8" cy="8" r="7" />
                </svg>
            );
        case "circle-dashed":
            return (
                <svg viewBox="0 0 16 16" strokeDasharray="3 2" {...svgProps}>
                    <circle cx="8" cy="8" r="7" />
                </svg>
            );
        case "rect":
            return (
                <svg viewBox="0 0 20 14" {...svgProps}>
                    <rect x="1" y="1" width="18" height="12" />
                </svg>
            );
        case "rect-dashed":
            return (
                <svg viewBox="0 0 20 14" strokeDasharray="4 2" {...svgProps}>
                    <rect x="1" y="1" width="18" height="12" />
                </svg>
            );
        case "square":
            return (
                <svg viewBox="0 0 14 14" {...svgProps}>
                    <rect x="1" y="1" width="12" height="12" />
                </svg>
            );
        case "diamond":
            return (
                <svg viewBox="0 0 16 16" {...svgProps}>
                    <polygon points="8,1 15,8 8,15 1,8" />
                </svg>
            );
        case "diamond-dashed":
            return (
                <svg viewBox="0 0 16 16" strokeDasharray="3 2" {...svgProps}>
                    <polygon points="8,1 15,8 8,15 1,8" />
                </svg>
            );
        default:
            assertNever(frame);
    }
}
