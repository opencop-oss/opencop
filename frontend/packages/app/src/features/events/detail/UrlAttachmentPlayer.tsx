// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { UrlAttachment } from "@opencop/api";
import { assertNever } from "assert-never";
import { lazy, Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Center, Spinner, Text } from "@chakra-ui/react";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { HlsPlayer } from "./HlsPlayer";
import { CameraImageFeed } from "./CameraImageFeed";

// Only an MPEG-TS attachment needs the mpegts.js engine, so it stays out of the main bundle.
const MpegTsPlayer = lazy(() =>
    import("./MpegTsPlayer").then((module) => ({ default: module.MpegTsPlayer })),
);

interface UrlAttachmentPlayerProps {
    attachment: UrlAttachment;
    sourceId: string;
    objectId: string;
    style?: React.CSSProperties;
    /** Fill the parent box, letterboxed on black, instead of taking the stream's own height. */
    fill?: boolean;
}

/** Routes a URL attachment to the player its stored transport requires. */
export function UrlAttachmentPlayer({
    attachment,
    sourceId,
    objectId,
    style,
    fill,
}: UrlAttachmentPlayerProps) {
    const { t } = useTranslation();
    const { streamKind, url, id } = attachment.record;

    switch (streamKind) {
        case "RTSP":
        case "SRT":
        case "HLS":
            return (
                <HlsPlayer
                    url={url}
                    streamKind={streamKind}
                    sourceId={sourceId}
                    objectId={objectId}
                    attachmentId={id}
                    style={style}
                    fill={fill}
                />
            );
        case "MJPEG":
            return <CameraImageFeed url={url} style={style} fill={fill} />;
        case "MPEGTS":
            return (
                // Suspense only covers the pending chunk; a chunk that fails to load (stale after a
                // deploy, flaky network) throws, and without this boundary it would replace the
                // whole Info tab rather than just the stream.
                // Retry reloads: lazy() caches a rejected import, and a stale chunk only resolves after a reload.
                <ErrorBoundaryWrapper
                    componentName="MpegTsPlayer"
                    onReset={() => window.location.reload()}
                >
                    <Suspense
                        fallback={
                            <Center py={4} h={fill ? "full" : undefined}>
                                <Spinner size="sm" color={fill ? "whiteAlpha.800" : "fg.muted"} />
                            </Center>
                        }
                    >
                        <MpegTsPlayer key={url} url={url} style={style} fill={fill} />
                    </Suspense>
                </ErrorBoundaryWrapper>
            );
        default: {
            // Compile-time exhaustiveness without the runtime throw: a newer backend can send a
            // kind this build doesn't know, and one such attachment must not take the panel down.
            assertNever(streamKind, true);
            return (
                <Center py={4} h={fill ? "full" : undefined}>
                    <Text
                        fontSize="sm"
                        color={fill ? "whiteAlpha.800" : "fg.muted"}
                        textAlign="center"
                    >
                        {t("events.streamKindUnsupported")}
                    </Text>
                </Center>
            );
        }
    }
}
