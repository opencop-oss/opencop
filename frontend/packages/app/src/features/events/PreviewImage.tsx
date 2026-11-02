// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    useEffect,
    useMemo,
    useRef,
    useSyncExternalStore,
    type CSSProperties,
    type ReactElement,
    type ReactNode,
} from "react";
import { noop } from "lodash";

type Status = "ready" | "loading" | "failed";

/** What a loaded or failed image settled to, once its `load` or `error` has fired. */
const settled = new WeakMap<HTMLImageElement, Status>();

function statusOf(source: CanvasImageSource): Status {
    if (!(source instanceof HTMLImageElement)) return "ready";
    const known = settled.get(source);
    if (known) return known;
    if (!source.complete) return "loading";
    return source.naturalWidth > 0 ? "ready" : "failed";
}

function subscribeTo(source: CanvasImageSource) {
    return (onChange: () => void): (() => void) => {
        if (!(source instanceof HTMLImageElement)) return noop;
        const onLoad = () => {
            settled.set(source, "ready");
            onChange();
        };
        const onError = () => {
            settled.set(source, "failed");
            onChange();
        };
        source.addEventListener("load", onLoad);
        source.addEventListener("error", onError);
        return () => {
            source.removeEventListener("load", onLoad);
            source.removeEventListener("error", onError);
        };
    };
}

/** The intrinsic size of a drawable image, for fitting it into the box; zero when unknown (fill the box). */
function sizeOf(source: CanvasImageSource): { width: number; height: number } {
    if (source instanceof HTMLImageElement)
        return { width: source.naturalWidth, height: source.naturalHeight };
    if (source instanceof HTMLVideoElement)
        return { width: source.videoWidth, height: source.videoHeight };
    const { width, height } = source as { width?: unknown; height?: unknown };
    return typeof width === "number" && typeof height === "number"
        ? { width, height }
        : { width: 0, height: 0 };
}

/**
 * Draws a contributed thumbnail — any image a canvas can draw — into a
 * `size`×`size` box at the screen's pixel density, scaled to fit. An image that
 * is still loading is drawn when it loads. With no source, or an image that fails
 * to load, it renders `fallback` instead.
 */
export function PreviewImage(props: PreviewImageProps): ReactElement {
    const { source, fallback } = props;
    if (!source) return <>{fallback}</>;
    return <SourceCanvas {...props} source={source} />;
}

export interface PreviewImageProps {
    readonly source: CanvasImageSource | null;
    /** The box's side, in CSS pixels. */
    readonly size: number;
    /** The image's accessible name; without one it is decorative. */
    readonly label?: string;
    readonly fallback: ReactNode;
    /** Extra styles for the drawn image's canvas, such as a backdrop behind transparent pixels. */
    readonly style?: CSSProperties;
}

function SourceCanvas({
    source,
    size,
    label,
    fallback,
    style,
}: PreviewImageProps & { readonly source: CanvasImageSource }): ReactElement {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const subscribe = useMemo(() => subscribeTo(source), [source]);
    const status = useSyncExternalStore(subscribe, () => statusOf(source));

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas || status !== "ready") return;
        const dpr = window.devicePixelRatio ?? 1;
        canvas.width = size * dpr;
        canvas.height = size * dpr;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, size, size);
        const { width, height } = sizeOf(source);
        const scale = width > 0 && height > 0 ? Math.min(size / width, size / height) : 1;
        const drawWidth = width > 0 ? width * scale : size;
        const drawHeight = height > 0 ? height * scale : size;
        ctx.drawImage(
            source,
            (size - drawWidth) / 2,
            (size - drawHeight) / 2,
            drawWidth,
            drawHeight,
        );
    }, [source, size, status]);

    if (status === "failed") return <>{fallback}</>;
    return (
        <canvas
            ref={canvasRef}
            role={label ? "img" : undefined}
            aria-label={label}
            aria-hidden={label ? undefined : true}
            style={{ ...style, width: size, height: size, display: "block" }}
        />
    );
}
