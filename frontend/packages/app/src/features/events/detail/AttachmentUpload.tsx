// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Input, Flex, Text, IconButton, Button, Field } from "@chakra-ui/react";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaPlus, FaLink, FaArrowLeft } from "react-icons/fa";
import { getErrorMessage, type StreamKind } from "@opencop/api";
import { OcSelect } from "@opencop/ui";
import { useAttachmentMutations } from "../use-attachment-mutations.hook";
import { inferStreamKind } from "./attachment.util";

interface AttachmentUploadProps {
    sourceId: string;
    objectId: string;
    disabled?: boolean;
}

const MAX_FILE_SIZE = 5 * 1024 * 1024;

// Keep this allowlist aligned with formats supported by the backend attachment pipeline.
// Add new media types only after end-to-end upload support is available.
const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp", "image/svg+xml"];

type Mode = "file" | "url";

const URL_PLACEHOLDER: Record<StreamKind, string> = {
    RTSP: "rtsp://...",
    HLS: "https://....m3u8",
    MJPEG: "http://...",
    MPEGTS: "https://....ts",
    SRT: "srt://host:port?streamid=...",
};

const STREAM_KINDS = Object.keys(URL_PLACEHOLDER) as StreamKind[];

function isStreamKind(value: string): value is StreamKind {
    return (STREAM_KINDS as string[]).includes(value);
}

export function AttachmentUpload({ sourceId, objectId, disabled }: AttachmentUploadProps) {
    const { t } = useTranslation();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [fileError, setFileError] = useState<string | null>(null);
    const [urlError, setUrlError] = useState<string | null>(null);
    const [uploadingCount, setUploadingCount] = useState(0);
    const isUploading = uploadingCount > 0;
    const [successCount, setSuccessCount] = useState(0);
    const [isDragging, setIsDragging] = useState(false);
    const [mode, setMode] = useState<Mode>("file");
    const [urlInput, setUrlInput] = useState("");
    // Once the operator picks a kind themselves, stop overriding it as they keep typing.
    const [kindTouched, setKindTouched] = useState(false);
    const [pickedKind, setPickedKind] = useState<StreamKind>("RTSP");
    const selectedKind = kindTouched ? pickedKind : (inferStreamKind(urlInput) ?? pickedKind);

    const resetUrlForm = () => {
        setUrlInput("");
        setUrlError(null);
        setKindTouched(false);
        setPickedKind("RTSP");
    };

    const streamKindItems = useMemo(() => {
        const labels: Record<StreamKind, string> = {
            RTSP: t("events.streamKindRtsp"),
            HLS: t("events.streamKindHls"),
            MJPEG: t("events.streamKindCamera"),
            MPEGTS: t("events.streamKindMpegTs"),
            SRT: t("events.streamKindSrt"),
        };
        return STREAM_KINDS.map((value) => ({ value, label: labels[value] }));
    }, [t]);

    const { uploadAttachment, registerUrl, isRegisteringUrl } = useAttachmentMutations({
        sourceId,
        objectId,
    });

    const processFiles = async (files: File[]) => {
        setFileError(null);
        setSuccessCount(0);

        const validFiles: File[] = [];
        const invalidMessages: string[] = [];

        for (const file of files) {
            if (!ACCEPTED_TYPES.includes(file.type)) {
                invalidMessages.push(`${file.name}: ${t("events.invalidFileType")}`);
                continue;
            }
            if (file.size > MAX_FILE_SIZE) {
                invalidMessages.push(`${file.name}: ${t("events.fileTooLarge")}`);
                continue;
            }
            validFiles.push(file);
        }

        if (invalidMessages.length > 0) {
            setFileError(invalidMessages.join("\n"));
        }

        if (validFiles.length > 0) {
            setUploadingCount(validFiles.length);

            const results = await Promise.all(
                validFiles.map(async (file) => {
                    try {
                        await uploadAttachment(file);
                        return { ok: true as const };
                    } catch {
                        return {
                            ok: false as const,
                            msg: `${file.name}: ${t("events.uploadFailed")}`,
                        };
                    }
                }),
            );
            const failed = results.filter((r) => !r.ok).map((r) => r.msg!);
            setSuccessCount(results.length - failed.length);
            if (failed.length) setFileError((cur) => [cur, ...failed].filter(Boolean).join("\n"));

            setUploadingCount(0);
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
        } else if (invalidMessages.length === files.length && fileInputRef.current) {
            fileInputRef.current.value = "";
        }
    };

    const handleFileSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const files = event.target.files;
        if (!files || files.length === 0) return;
        await processFiles(Array.from(files));
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        if (!disabled && !isUploading) setIsDragging(true);
    };

    const handleDragLeave = (e: React.DragEvent) => {
        e.preventDefault();
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setIsDragging(false);
    };

    const handleDrop = async (e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);
        if (disabled || isUploading) return;
        const files = Array.from(e.dataTransfer.files);
        if (files.length === 0) return;
        await processFiles(files);
    };

    const handleUrlSubmit = async () => {
        setUrlError(null);
        const trimmed = urlInput.trim();
        if (!trimmed) return;

        const normalized = trimmed.replace(/^(rtsps?|https?|srt):/i, (m) => m.toLowerCase());
        if (selectedKind === "RTSP") {
            if (!normalized.startsWith("rtsp://") && !normalized.startsWith("rtsps://")) {
                setUrlError(t("events.invalidRtspUrl"));
                return;
            }
        } else if (selectedKind === "SRT") {
            if (!normalized.startsWith("srt://")) {
                setUrlError(t("events.invalidSrtUrl"));
                return;
            }
        } else if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            setUrlError(t("events.invalidHttpStreamUrl"));
            return;
        }

        try {
            await registerUrl(normalized, selectedKind);
            resetUrlForm();
            setMode("file");
        } catch (err) {
            setUrlError(getErrorMessage(err) ?? t("events.uploadFailed"));
        }
    };

    const handleUrlKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "Enter") handleUrlSubmit();
    };

    if (mode === "url") {
        return (
            <Flex direction="column" gap={2} minWidth="full">
                <Flex gap={2} align="center">
                    <IconButton
                        size="xs"
                        variant="ghost"
                        aria-label={t("common.back")}
                        onClick={() => {
                            setMode("file");
                            resetUrlForm();
                        }}
                        disabled={isRegisteringUrl}
                    >
                        <FaArrowLeft />
                    </IconButton>
                    <Text fontSize="sm" fontWeight={500} color="fg">
                        {t("events.addStreamOrCamera")}
                    </Text>
                </Flex>

                <Field.Root minWidth="full">
                    <Field.Label>{t("events.streamKindLabel")}</Field.Label>
                    <OcSelect
                        items={streamKindItems}
                        value={selectedKind}
                        onValueChange={(value) => {
                            if (!value || !isStreamKind(value)) return;
                            setKindTouched(true);
                            setPickedKind(value);
                        }}
                        disabled={disabled || isRegisteringUrl}
                        size="sm"
                        variant="outline"
                    />
                </Field.Root>

                <Field.Root invalid={!!urlError} minWidth="full">
                    <Field.Label>{t("events.streamUrlLabel")}</Field.Label>
                    <Flex gap={2} minWidth="full">
                        <Input
                            flex={1}
                            size="sm"
                            flexGrow={1}
                            placeholder={URL_PLACEHOLDER[selectedKind]}
                            aria-label={t("events.streamUrlLabel")}
                            value={urlInput}
                            onChange={(e) => setUrlInput(e.target.value)}
                            onKeyDown={handleUrlKeyDown}
                            disabled={disabled || isRegisteringUrl}
                        />
                        <IconButton
                            size="sm"
                            aria-label={t("common.add")}
                            onClick={handleUrlSubmit}
                            disabled={disabled || isRegisteringUrl || !urlInput.trim()}
                            loading={isRegisteringUrl}
                        >
                            <FaPlus />
                        </IconButton>
                    </Flex>
                    <Field.HelperText fontSize="xs">{t("events.streamUrlHelp")}</Field.HelperText>
                    <Field.ErrorText whiteSpace="pre-wrap">{urlError}</Field.ErrorText>
                </Field.Root>
            </Flex>
        );
    }

    return (
        <Flex direction="column" gap={2}>
            <Flex
                as="label"
                direction="row"
                align="center"
                gap="3"
                px={4}
                py={3}
                borderRadius="xl"
                border="1px dashed"
                borderColor={fileError ? "red.500" : isDragging ? "border" : "border.muted"}
                bg={isDragging ? "glass" : "glass.subtle"}
                cursor={disabled || isUploading ? "not-allowed" : "pointer"}
                opacity={disabled ? 0.6 : 1}
                transition="background-color 0.2s ease-out, border-color 0.2s ease-out"
                _hover={{
                    borderColor: disabled || isUploading ? undefined : "border",
                    bg: disabled || isUploading ? undefined : "glass",
                }}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
            >
                <FaPlus size={16} />
                <Flex direction="column" gap="0.5" minW={0}>
                    <Text fontSize="sm" color="fg" fontWeight={500} lineHeight="short">
                        {isUploading
                            ? `${t("events.uploading")} (${successCount}/${uploadingCount})`
                            : isDragging
                              ? t("events.dropToUpload")
                              : t("events.addMedia")}
                    </Text>
                    <Text fontSize="xs" color="fg.muted" opacity={0.8} lineHeight="short">
                        {t("events.imageOnly")} • {t("events.maxSize5mb")} •{" "}
                        {t("events.multipleFilesSupported")}
                    </Text>
                </Flex>

                <Input
                    ref={fileInputRef}
                    type="file"
                    accept={ACCEPTED_TYPES.join(",")}
                    onChange={handleFileSelect}
                    disabled={disabled || isUploading}
                    display="none"
                    multiple
                />
            </Flex>

            <Button
                variant="ghost"
                w="full"
                h="auto"
                px={4}
                py="2.5"
                borderRadius="xl"
                border="1px solid"
                borderColor="border.muted"
                bg="glass.subtle"
                justifyContent="flex-start"
                gap="3"
                disabled={disabled}
                _hover={{ borderColor: "border", bg: "glass" }}
                transition="background-color 0.2s ease-out, border-color 0.2s ease-out"
                onClick={() => setMode("url")}
            >
                <FaLink size={14} />
                <Text fontSize="sm" color="fg" fontWeight={500} lineHeight="short">
                    {t("events.addStreamOrCamera")}
                </Text>
            </Button>

            {fileError && (
                <Text fontSize="xs" color="red.500" textAlign="center" whiteSpace="pre-wrap">
                    {fileError}
                </Text>
            )}
        </Flex>
    );
}
