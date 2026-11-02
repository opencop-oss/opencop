// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack, Separator, HStack, Box, Flex, Stack, Text } from "@chakra-ui/react";
import { OcSwitch } from "@opencop/ui";
import { useEffect, useRef, useMemo, useState, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { scrollbarStyles } from "@opencop/ui";
import { PageLayout } from "~/layouts/PageLayout";
import { useAuth } from "~/features/auth/AuthContext";
import {
    preferencesQueries,
    preferencesMutations,
} from "~/features/preferences/preferences.queries";
import { detectTimezone } from "~/features/preferences/timezone.util";
import { PreferenceRow } from "~/features/preferences/PreferenceRow";
import { PreferencesNav, type PreferencesSectionId } from "~/features/preferences/PreferencesNav";
import { AvatarUpload } from "~/features/preferences/AvatarUpload";
import { AccountSection } from "~/features/preferences/AccountSection";
import { MapOptionsSection } from "~/features/preferences/MapOptionsSection";
import { LocalizationSection } from "~/features/preferences/LocalizationSection";

export function UserPreferencesView() {
    const { t } = useTranslation();
    const { user } = useAuth();
    const queryClient = useQueryClient();

    const detectedTimezone = useMemo(() => detectTimezone(), []);

    const { data: preferences } = useQuery(preferencesQueries.current());
    const { mutate: updatePreferencesMutate } = useMutation(
        preferencesMutations.update(queryClient),
    );

    const hasAutoSavedTimezoneRef = useRef(false);

    useEffect(() => {
        if (preferences && preferences.timezone === null && !hasAutoSavedTimezoneRef.current) {
            hasAutoSavedTimezoneRef.current = true;
            updatePreferencesMutate({ timezone: detectedTimezone });
        }
    }, [detectedTimezone, preferences, updatePreferencesMutate]);

    const email = user?.user.email || "";
    const defaultUsername = email.split("@")[0]?.trim() || "";
    const persistedUsername = preferences?.username.trim() || null;
    const isUsernameReady = preferences !== undefined;

    const [activeSection, setActiveSection] = useState<PreferencesSectionId>("account");
    const contentRef = useRef<HTMLDivElement | null>(null);

    const sectionRefsRef = useRef<Record<PreferencesSectionId, HTMLDivElement | null>>({
        account: null,
        profile: null,
        "map-options": null,
        localization: null,
        display: null,
    });

    const handleSectionChange = useCallback((sectionId: PreferencesSectionId) => {
        setActiveSection(sectionId);

        const content = contentRef.current;
        const element = sectionRefsRef.current[sectionId];
        if (content && element) {
            const contentTop = content.getBoundingClientRect().top;
            const elementTop = element.getBoundingClientRect().top;
            const nextScrollTop = content.scrollTop + (elementTop - contentTop);

            content.scrollTo({ top: nextScrollTop, behavior: "smooth" });
        }
    }, []);

    return (
        <PageLayout
            title={t("userPreferences.title", { defaultValue: "User Preferences" })}
            subtitle={t("userPreferences.subtitle", { defaultValue: "Account & Settings" })}
            componentName="UserPreferences"
        >
            <Flex gap={6} p={6} flex="1" minH="0" overflow="hidden">
                {/* Navigation Sidebar */}
                <Box flexShrink={0} position="sticky" top="6" alignSelf="start">
                    <PreferencesNav
                        activeSection={activeSection}
                        onSectionChange={handleSectionChange}
                    />
                </Box>

                {/* Content Area with custom scrollbar */}
                <VStack
                    ref={contentRef}
                    align="stretch"
                    gap={6}
                    flex="1"
                    minH="0"
                    maxW="xl"
                    overflowY="auto"
                    css={scrollbarStyles}
                >
                    <AccountSection
                        user={user}
                        persistedUsername={persistedUsername}
                        suggestedUsername={defaultUsername}
                        isUsernameReady={isUsernameReady}
                        ref={(el) => {
                            sectionRefsRef.current.account = el;
                        }}
                    />

                    <Separator />

                    <Stack
                        gap={4}
                        ref={(el) => {
                            sectionRefsRef.current.profile = el;
                        }}
                        id="profile"
                    >
                        <Text fontSize="lg" fontWeight="semibold" color="fg">
                            {t("userPreferences.profileSection", { defaultValue: "Profile" })}
                        </Text>
                        <AvatarUpload />
                    </Stack>

                    <Separator />

                    <MapOptionsSection
                        preferences={preferences}
                        ref={(el) => {
                            sectionRefsRef.current["map-options"] = el;
                        }}
                    />

                    <Separator />

                    <LocalizationSection
                        preferences={preferences}
                        ref={(el) => {
                            sectionRefsRef.current.localization = el;
                        }}
                    />

                    <Separator />

                    <Stack
                        gap={4}
                        ref={(el) => {
                            sectionRefsRef.current.display = el;
                        }}
                        id="display"
                    >
                        <Text fontSize="lg" fontWeight="semibold" color="fg">
                            {t("userPreferences.displaySection", { defaultValue: "Display" })}
                        </Text>
                        <PreferenceRow
                            label={t("userPreferences.darkMode", {
                                defaultValue: "Light Mode / Dark Mode",
                            })}
                            description={t("userPreferences.darkModeNote", {
                                defaultValue: "Will be implemented in future versions",
                            })}
                            alignItems="start"
                        >
                            <HStack justify="flex-end">
                                <OcSwitch
                                    label={t("userPreferences.darkMode", {
                                        defaultValue: "Light Mode / Dark Mode",
                                    })}
                                    srOnlyLabel
                                    fullWidth={false}
                                    variant="placeholder"
                                    checked
                                    disabled
                                    onCheckedChange={() => {}}
                                />
                            </HStack>
                        </PreferenceRow>
                    </Stack>
                </VStack>
            </Flex>
        </PageLayout>
    );
}
