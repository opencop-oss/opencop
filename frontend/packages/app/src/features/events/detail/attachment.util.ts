// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FileAttachment, StreamKind, UrlAttachment, UrlAttachmentRecord } from "@opencop/api";

export function getThumbnailUrl(attachment: FileAttachment): string | null {
    return (
        attachment.thumbnails?.[0]?.url ??
        (attachment.record.contentType === "IMAGE" ? attachment.url : null)
    );
}

/** Infers the stream transport from a URL's scheme/extension; null when it can't be determined yet. */
export function inferStreamKind(url: string): StreamKind | null {
    const trimmed = url.trim();
    const lower = trimmed.toLowerCase();
    if (lower.startsWith("rtsp://") || lower.startsWith("rtsps://")) return "RTSP";
    if (lower.startsWith("srt://")) return "SRT";
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null;
    try {
        const path = new URL(trimmed).pathname.toLowerCase();
        if (path.endsWith(".m3u8")) return "HLS";
        return path.endsWith(".ts") ? "MPEGTS" : "MJPEG";
    } catch {
        return "MJPEG";
    }
}

const STREAM_KIND_NAMES: Record<StreamKind, string> = {
    RTSP: "RTSP",
    SRT: "SRT",
    HLS: "HLS",
    MPEGTS: "MPEG-TS",
    MJPEG: "MJPEG",
};

/** Protocol names are not translated: they are the same in every locale. */
export function streamKindName(kind: StreamKind): string {
    // A newer backend can send a kind this build has no name for; show it rather than "undefined".
    return STREAM_KIND_NAMES[kind] ?? kind;
}

/** MJPEG URLs are camera feeds; every other kind is a video stream. */
export function isCameraFeed(kind: StreamKind): boolean {
    return kind === "MJPEG";
}

/**
 * Just the protocol and domain of a URL, so streams on different hosts can be told apart without
 * showing ports, paths, stream ids or tokens. Embedded credentials are dropped.
 */
export function streamUrlTitle(url: string): string {
    const trimmed = url.trim();
    const parts = /^([a-z][a-z0-9+.-]*:\/\/)(?:[^/@?#]*@)?(\[[^\]]*\]|[^/:?#]*)/i.exec(trimmed);
    return parts ? `${parts[1]}${parts[2]}` : (trimmed.split(/[?#]/)[0] ?? "");
}

/** "srt://relay.example.com · SRT" — what tells one URL attachment from another. */
export function urlAttachmentTitle(record: UrlAttachmentRecord): string {
    return `${streamUrlTitle(record.url)} · ${streamKindName(record.streamKind)}`;
}

/** An http URL on an https page is blocked with no actionable error, so callers report it. */
export function isBlockedMixedContent(url: string): boolean {
    return window.location.protocol === "https:" && url.trim().toLowerCase().startsWith("http://");
}

/** Source-attached streams carry no creator; operators may not delete them. */
export function isSourceOwnedAttachment(attachment: UrlAttachment): boolean {
    return attachment.record.creatorId === null;
}

/** Translation key for a URL attachment's display label, shared by the card and the carousel title. */
export function urlAttachmentLabelKey(
    record: UrlAttachmentRecord,
): "events.cameraFeed" | "events.liveStream" {
    return isCameraFeed(record.streamKind) ? "events.cameraFeed" : "events.liveStream";
}
