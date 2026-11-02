// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement, ReactNode, SVGProps } from "react";
import type { DetailsTypeIconProps } from "@opencop/contributions";

/** The details header's glyph frame: a glowing 20px icon in the element's accent colour. */
function Glyph({
    color,
    ariaLabel,
    stroke,
    children,
}: DetailsTypeIconProps & { stroke?: boolean; children: ReactNode }): ReactElement {
    const props: SVGProps<SVGSVGElement> = {
        width: 20,
        height: 20,
        viewBox: "0 0 24 24",
        fill: stroke ? "none" : color,
        filter: `drop-shadow(0px 0px 4px ${color})`,
        ...(stroke ? { stroke: color, strokeWidth: 3 } : null),
        ...(ariaLabel ? { role: "img", "aria-label": ariaLabel } : null),
    };
    return <svg {...props}>{children}</svg>;
}

export function PointGlyph(props: DetailsTypeIconProps): ReactElement {
    return (
        <Glyph {...props}>
            <ellipse cx="12" cy="12" rx="4" ry="4" />
        </Glyph>
    );
}

export function LineGlyph(props: DetailsTypeIconProps): ReactElement {
    return (
        <Glyph {...props} stroke>
            <line x1="4" y1="20" x2="20" y2="4" />
        </Glyph>
    );
}

export function PolygonGlyph(props: DetailsTypeIconProps): ReactElement {
    return (
        <Glyph {...props}>
            <polygon points="12,2 22,20 2,20" />
        </Glyph>
    );
}

export function EllipseGlyph(props: DetailsTypeIconProps): ReactElement {
    return (
        <Glyph {...props}>
            <ellipse cx="12" cy="12" rx="10" ry="6" />
        </Glyph>
    );
}

export function ArrowGlyph(props: DetailsTypeIconProps): ReactElement {
    return (
        <Glyph {...props}>
            <polygon points="12,2 20,12 14,12 14,22 10,22 10,12 4,12" />
        </Glyph>
    );
}
