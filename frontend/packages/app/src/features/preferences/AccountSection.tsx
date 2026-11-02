// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, Input, Text, Stack } from "@chakra-ui/react";
import { useCallback, useEffect, useRef, useState, type Ref } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { ApiError, getErrorMessage } from "@opencop/api";
import { CopyButton } from "@opencop/ui";
import type { AuthUser } from "@opencop/api";
import { PreferenceField } from "./PreferenceField";
import { preferencesMutations } from "./preferences.queries";

interface AccountSectionProps {
    user: AuthUser | null;
    persistedUsername: string | null;
    suggestedUsername: string;
    isUsernameReady: boolean;
    ref?: Ref<HTMLDivElement>;
}

export function AccountSection({
    user,
    persistedUsername,
    suggestedUsername,
    isUsernameReady,
    ref,
}: AccountSectionProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutate: updatePreferencesMutate, isPending: isUpdatingUsername } = useMutation(
        preferencesMutations.update(queryClient),
    );

    const displayName = user?.user.displayName || "";
    const email = user?.user.email || "";

    const [editingUsername, setEditingUsername] = useState<string | null>(null);
    const [usernameError, setUsernameError] = useState<string | null>(null);
    const [showSavedIndicator, setShowSavedIndicator] = useState(false);
    const savedIndicatorTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const committedUsername = persistedUsername ?? suggestedUsername;
    const displayUsername = editingUsername ?? (isUsernameReady ? committedUsername : "");

    // Effect needed to clean up browser timer on unmount (external system synchronization)
    useEffect(() => {
        return () => {
            if (savedIndicatorTimeoutRef.current) {
                clearTimeout(savedIndicatorTimeoutRef.current);
            }
        };
    }, []);

    const hideSavedIndicator = useCallback(() => {
        if (savedIndicatorTimeoutRef.current) {
            clearTimeout(savedIndicatorTimeoutRef.current);
            savedIndicatorTimeoutRef.current = null;
        }
        setShowSavedIndicator(false);
    }, []);

    const showSavedSuccess = useCallback(() => {
        hideSavedIndicator();
        setShowSavedIndicator(true);
        savedIndicatorTimeoutRef.current = setTimeout(() => {
            setShowSavedIndicator(false);
            savedIndicatorTimeoutRef.current = null;
        }, 2000);
    }, [hideSavedIndicator]);

    const commitUsername = useCallback(() => {
        if (editingUsername === null) return;

        const trimmedUsername = editingUsername.trim();

        if (!trimmedUsername) {
            setUsernameError(
                t("userPreferences.usernameRequired", {
                    defaultValue: "Username cannot be empty",
                }),
            );
            return;
        }

        if (trimmedUsername !== committedUsername) {
            setUsernameError(null);

            // Fire mutation without awaiting - React Query handles the state
            updatePreferencesMutate(
                { username: trimmedUsername },
                {
                    onSuccess: () => {
                        showSavedSuccess();
                        setEditingUsername(null);
                    },
                    onError: (error) => {
                        hideSavedIndicator();
                        setUsernameError(
                            error instanceof ApiError && error.status === 409
                                ? t("userPreferences.usernameTaken", {
                                      defaultValue: "That username is already taken",
                                  })
                                : getErrorMessage(
                                      error,
                                      t("userPreferences.usernameSaveFailed", {
                                          defaultValue: "Failed to save username",
                                      }),
                                  ),
                        );
                    },
                },
            );
            return;
        }

        setUsernameError(null);
        setEditingUsername(null);
    }, [
        committedUsername,
        editingUsername,
        hideSavedIndicator,
        showSavedSuccess,
        t,
        updatePreferencesMutate,
    ]);

    const handleUsernameFocus = useCallback(() => {
        if (!isUsernameReady || isUpdatingUsername) return;
        hideSavedIndicator();
        setUsernameError(null);
        setEditingUsername(committedUsername);
    }, [committedUsername, hideSavedIndicator, isUpdatingUsername, isUsernameReady]);

    return (
        <Stack gap={4} ref={ref} id="account">
            <Text fontSize="lg" fontWeight="semibold" color="fg">
                {t("userPreferences.accountSection", { defaultValue: "Account" })}
            </Text>
            <Box display="grid" gridTemplateColumns={{ base: "1fr", md: "1fr 1fr" }} gap={4}>
                <Stack gap={1}>
                    <PreferenceField
                        label={t("userPreferences.username", { defaultValue: "Username" })}
                        value={displayUsername}
                        onChange={(e) => {
                            hideSavedIndicator();
                            setUsernameError(null);
                            setEditingUsername(e.target.value);
                        }}
                        onFocus={handleUsernameFocus}
                        onBlur={commitUsername}
                        onKeyDown={(event) => {
                            if (event.key === "Enter") {
                                event.currentTarget.blur();
                            }
                        }}
                        placeholder={suggestedUsername}
                        disabled={!isUsernameReady || isUpdatingUsername}
                    />
                    {usernameError && (
                        <HStack gap={2} px={1}>
                            <Box
                                width="8px"
                                height="8px"
                                borderRadius="full"
                                bg="red.500"
                                flexShrink={0}
                            />
                            <Text fontSize="xs" color="red.500" fontWeight="medium">
                                {usernameError}
                            </Text>
                        </HStack>
                    )}
                    {showSavedIndicator && (
                        <HStack gap={2} px={1}>
                            <Box
                                width="8px"
                                height="8px"
                                borderRadius="full"
                                bg="type.success"
                                flexShrink={0}
                            />
                            <Text fontSize="xs" color="type.success" fontWeight="medium">
                                {t("userPreferences.usernameSaved", {
                                    defaultValue: "Username saved",
                                })}
                            </Text>
                        </HStack>
                    )}
                </Stack>

                <PreferenceField
                    label={t("userPreferences.email", { defaultValue: "Email" })}
                    value={email}
                    disabled
                />
            </Box>

            <PreferenceField
                label={t("userPreferences.fullName", { defaultValue: "Full Name" })}
                value={displayName}
                disabled
            />

            <Box>
                <Text fontSize="sm" fontWeight="medium" mb={2}>
                    {t("userPreferences.idpUserId", { defaultValue: "IDP User ID" })}
                </Text>
                <HStack gap={2} width="100%">
                    <Input
                        value={user?.user.id ?? ""}
                        disabled
                        bg="bg.muted"
                        cursor="not-allowed"
                        flex={1}
                    />
                    {user?.user.id && <CopyButton value={user.user.id} />}
                </HStack>
            </Box>

            <Text fontSize="sm" color="fg.muted" fontStyle="italic">
                {t("userPreferences.contactAdmin", {
                    defaultValue:
                        "To change your account details, please contact your administrator.",
                })}
            </Text>
        </Stack>
    );
}
