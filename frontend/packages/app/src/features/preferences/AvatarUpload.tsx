// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, IconButton, Image, Input, Spinner, Text, VStack } from "@chakra-ui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { MdDelete, MdUpload } from "react-icons/md";
import type { ThumbnailRef, UserPreferences } from "@opencop/api";
import { preferencesMutations, preferencesQueries } from "./preferences.queries";
import Logger from "js-logger";
import { AvatarCropModal } from "./AvatarCropModal";

const logger = Logger.get("preferences");

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp", "image/svg+xml"];

function hasDimensions(
    thumbnail: ThumbnailRef,
): thumbnail is ThumbnailRef & { widthPx: number; heightPx: number } {
    return thumbnail.widthPx !== null && thumbnail.heightPx !== null;
}

function getAvatarPreviewUrl(avatar: NonNullable<UserPreferences["avatar"]>) {
    const sizedThumbnails = avatar.thumbnails?.filter(hasDimensions) ?? [];

    const sortedThumbnails = [...sizedThumbnails].sort(
        (left, right) => left.widthPx * left.heightPx - right.widthPx * right.heightPx,
    );

    const thumbnail64 = sortedThumbnails.find(
        (thumbnail) => thumbnail.widthPx >= 64 && thumbnail.heightPx >= 64,
    );

    return thumbnail64?.url ?? sortedThumbnails[0]?.url ?? avatar.url;
}

export function AvatarUpload() {
    const { t } = useTranslation();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [error, setError] = useState<string | null>(null);
    const [croppingFile, setCroppingFile] = useState<File | null>(null);

    const queryClient = useQueryClient();
    const { data: preferences } = useQuery(preferencesQueries.current());

    const { mutateAsync: uploadAvatar, isPending: isUploading } = useMutation(
        preferencesMutations.avatar.upload(queryClient),
    );
    const { mutateAsync: deleteAvatar, isPending: isDeleting } = useMutation(
        preferencesMutations.avatar.delete(queryClient),
    );

    const avatar = preferences?.avatar;
    const isProcessing = isUploading || isDeleting;
    const avatarPreviewUrl = useMemo(() => {
        if (!avatar) return null;
        return getAvatarPreviewUrl(avatar);
    }, [avatar]);

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const files = event.target.files;
        if (!files || files.length === 0) return;

        // Prevent race conditions if already processing
        if (isProcessing) {
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
            return;
        }

        setError(null);
        const file = files[0]; // Only take first file for avatar

        // Validate file type
        if (!ACCEPTED_TYPES.includes(file.type)) {
            setError(t("userPreferences.invalidAvatarType"));
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
            return;
        }

        if (file.size > MAX_FILE_SIZE) {
            setError(t("userPreferences.avatarTooLarge"));
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
            return;
        }

        setCroppingFile(file);
        if (fileInputRef.current) fileInputRef.current.value = "";
    };

    const handleCropSave = async (croppedFile: File) => {
        try {
            await uploadAvatar(croppedFile);
            setCroppingFile(null);
        } catch {
            setError(t("userPreferences.avatarUploadFailed"));
            setCroppingFile(null);
        }
    };

    const handleCropClose = () => {
        setCroppingFile(null);
    };

    const handleDeleteAvatar = async (event: React.MouseEvent) => {
        event.stopPropagation();
        setError(null);
        try {
            await deleteAvatar();
        } catch (error) {
            logger.error("Avatar deletion failed:", error);
            setError(t("userPreferences.avatarDeleteFailed"));
        }
    };

    return (
        <>
            <VStack align="stretch" gap={2}>
                <Flex justify="space-between" align="center">
                    <Box
                        as="label"
                        position="relative"
                        width="64px"
                        height="64px"
                        borderRadius="md"
                        borderWidth="2px"
                        borderStyle={avatar ? "solid" : "dashed"}
                        borderColor={error ? "red.500" : "border.muted"}
                        bg="bg.muted"
                        overflow="hidden"
                        cursor={isProcessing ? "not-allowed" : "pointer"}
                        display="flex"
                        alignItems="center"
                        justifyContent="center"
                        transition="border-color 0.2s, background-color 0.2s"
                        _hover={
                            !isProcessing
                                ? {
                                      borderColor: "border",
                                      bg: avatar ? "bg.muted" : "glass",
                                      "& .avatar-overlay": {
                                          opacity: 1,
                                      },
                                  }
                                : undefined
                        }
                    >
                        {isProcessing ? (
                            <Spinner size="md" color="fg.muted" />
                        ) : avatar ? (
                            <>
                                <Image
                                    src={avatarPreviewUrl ?? avatar.url}
                                    alt={t("userPreferences.avatar")}
                                    width="100%"
                                    height="100%"
                                    objectFit="cover"
                                />
                                <Flex
                                    className="avatar-overlay"
                                    position="absolute"
                                    inset="0"
                                    align="center"
                                    justify="center"
                                    bg="blackAlpha.600"
                                    opacity={0}
                                    transition="opacity 0.2s"
                                >
                                    <MdUpload size={24} color="white" />
                                </Flex>
                            </>
                        ) : (
                            <MdUpload size={24} color="var(--chakra-colors-fg-muted)" />
                        )}
                        <Input
                            ref={fileInputRef}
                            type="file"
                            accept={ACCEPTED_TYPES.join(",")}
                            onChange={handleFileSelect}
                            disabled={isProcessing}
                            display="none"
                        />
                    </Box>

                    <VStack align="start" gap={1} flex={1} px={4}>
                        <Text fontWeight="medium" color="fg">
                            {t("userPreferences.avatar")}
                        </Text>
                        <Text fontSize="xs" color="fg.muted">
                            {t("userPreferences.avatarDescription")}
                        </Text>
                    </VStack>

                    {avatar && (
                        <IconButton
                            aria-label={t("userPreferences.deleteAvatar")}
                            size="sm"
                            variant="ghost"
                            colorScheme="red"
                            onClick={handleDeleteAvatar}
                            disabled={isProcessing}
                        >
                            <MdDelete />
                        </IconButton>
                    )}
                </Flex>

                {error && (
                    <Text fontSize="xs" color="red.500" textAlign="center">
                        {error}
                    </Text>
                )}

                <Text fontSize="xs" color="fg.muted" opacity={0.7}>
                    {t("userPreferences.avatarRequirements")}
                </Text>
            </VStack>

            {croppingFile && (
                <AvatarCropModal
                    file={croppingFile}
                    onClose={handleCropClose}
                    onSave={handleCropSave}
                    isSaving={isUploading}
                />
            )}
        </>
    );
}
