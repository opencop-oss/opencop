// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Dialog, IconButton, Text, VStack } from "@chakra-ui/react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import AvatarEditor, { type AvatarEditorRef } from "react-avatar-editor";
import { MdRotateRight } from "react-icons/md";
import {
    BaseDialog,
    DialogActions,
    DialogHeader,
    OcSlider,
    THUMBNAIL_FORMAT,
    THUMBNAIL_QUALITY,
} from "@opencop/ui";

interface AvatarCropModalProps {
    file: File;
    onClose: () => void;
    onSave: (croppedFile: File) => Promise<void>;
    isSaving: boolean;
}

const PREVIEW_SIZE = 300;
const OUTPUT_SIZE = 256;
const MIN_ZOOM = 1;
const MAX_ZOOM = 4;

export function AvatarCropModal({ file, onClose, onSave, isSaving }: AvatarCropModalProps) {
    const { t } = useTranslation();
    const editorRef = useRef<AvatarEditorRef>(null);
    const canvasBoxRef = useRef<HTMLDivElement>(null);

    const [zoom, setZoom] = useState(1);
    const [rotation, setRotation] = useState(0);
    const [saveError, setSaveError] = useState<string | null>(null);

    useEffect(() => {
        const el = canvasBoxRef.current;
        if (!el) return;
        const onWheel = (e: WheelEvent) => {
            e.preventDefault();
            setZoom((prev) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, prev + -e.deltaY * 0.005)));
        };
        el.addEventListener("wheel", onWheel, { passive: false });
        return () => el.removeEventListener("wheel", onWheel);
    }, []);

    const handleRotate = () => setRotation((r) => (r + 90) % 360);

    const handleSave = async () => {
        const editor = editorRef.current;
        if (!editor) return;

        setSaveError(null);

        const editorCanvas = editor.getImageScaledToCanvas();
        const outputCanvas = document.createElement("canvas");
        outputCanvas.width = OUTPUT_SIZE;
        outputCanvas.height = OUTPUT_SIZE;
        const ctx = outputCanvas.getContext("2d");
        if (!ctx) return;

        ctx.drawImage(editorCanvas, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

        const blob = await new Promise<Blob | null>((resolve) =>
            outputCanvas.toBlob(resolve, THUMBNAIL_FORMAT, THUMBNAIL_QUALITY),
        );

        if (!blob) {
            setSaveError(t("userPreferences.avatarSaveFailed"));
            return;
        }

        try {
            await onSave(
                new File([blob], file.name.replace(/\.[^.]+$/, ".webp"), {
                    type: THUMBNAIL_FORMAT,
                }),
            );
        } catch {
            setSaveError(t("userPreferences.avatarUploadFailed"));
        }
    };

    return (
        <BaseDialog open onClose={onClose} size="sm">
            <DialogHeader title={t("userPreferences.cropAvatar")} />

            <Dialog.Body>
                <VStack gap={4} align="center">
                    <Box ref={canvasBoxRef} borderRadius="md" overflow="hidden" flexShrink={0}>
                        <AvatarEditor
                            ref={editorRef}
                            image={file}
                            width={PREVIEW_SIZE}
                            height={PREVIEW_SIZE}
                            border={0}
                            borderRadius={0}
                            scale={zoom}
                            rotate={rotation}
                            color={[0, 0, 0, 0.6]}
                        />
                    </Box>

                    <Text fontSize="xs" color="fg.muted" opacity={0.6}>
                        {t("userPreferences.cropHint")}
                    </Text>

                    <VStack width="80%" align="center">
                        <OcSlider
                            min={MIN_ZOOM}
                            max={MAX_ZOOM}
                            step={0.01}
                            value={zoom}
                            onChange={setZoom}
                        />
                        <IconButton
                            aria-label={t("userPreferences.rotate")}
                            variant="ghost"
                            size="md"
                            onClick={handleRotate}
                            disabled={isSaving}
                            flexShrink={0}
                        >
                            <MdRotateRight size={22} />
                        </IconButton>
                    </VStack>

                    {saveError && (
                        <Text fontSize="xs" color="red.500">
                            {saveError}
                        </Text>
                    )}
                </VStack>
            </Dialog.Body>

            <DialogActions
                onClose={onClose}
                cancelLabel={t("common.cancel")}
                onAction={handleSave}
                actionLabel={t("common.save")}
                loading={isSaving}
                loadingText={t("common.saving")}
            />
        </BaseDialog>
    );
}
