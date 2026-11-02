// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef, type ReactElement } from "react";
import type { Element } from "@opencop/elements";
import { resolveElementPreview } from "~/features/contributions/element-mapper-registry";
import { PreviewImage } from "./PreviewImage";

const SIZE = 40;

/**
 * Preview a single element at 40×40 — used inline in StackedElements rows. The
 * subject whose renderer draws the element supplies the thumbnail, resolved by
 * the same `match` that routes map rendering, so there is no per-subject branch
 * here. With no thumbnail, or one that fails to load, a faint dot stands in.
 */
export function ElementPreviewCanvas({ element }: { element: Element }): ReactElement {
    // Resolved on every render: an element changes its code and geometry in place.
    const source = resolveElementPreview(element);
    return <PreviewImage source={source} size={SIZE} fallback={<PlaceholderDot />} />;
}

/** The host's thumbnail when no subject supplies one: a faint dot. */
function PlaceholderDot(): ReactElement {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const dpr = window.devicePixelRatio ?? 1;
        canvas.width = SIZE * dpr;
        canvas.height = SIZE * dpr;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        ctx.scale(dpr, dpr);
        ctx.beginPath();
        ctx.arc(SIZE / 2, SIZE / 2, SIZE / 4, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(255,255,255,0.4)";
        ctx.fill();
    }, []);

    return (
        <canvas
            ref={canvasRef}
            aria-hidden
            style={{ width: SIZE, height: SIZE, display: "block" }}
        />
    );
}
