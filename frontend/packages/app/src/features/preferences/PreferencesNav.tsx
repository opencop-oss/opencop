// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack, Box, Flex, Text } from "@chakra-ui/react";
import { FaUser, FaGlobe, FaIdBadge } from "react-icons/fa6";
import { TbMap } from "react-icons/tb";
import { LuMoon } from "react-icons/lu";
import { useTranslation } from "react-i18next";
import type { ParseKeys } from "i18next";

// Define the available preferences sections with their corresponding icons and translation keys
// Using 'as const' without explicit type annotation to preserve literal types for proper type narrowing
const PREFERENCES_SECTIONS = [
    { id: "account", translationKey: "userPreferences.accountSection" as ParseKeys, icon: FaUser },
    {
        id: "profile",
        translationKey: "userPreferences.profileSection" as ParseKeys,
        icon: FaIdBadge,
    },
    {
        id: "map-options",
        translationKey: "userPreferences.mapOptionsSection" as ParseKeys,
        icon: TbMap,
    },
    {
        id: "localization",
        translationKey: "userPreferences.localizationSection" as ParseKeys,
        icon: FaGlobe,
    },
    { id: "display", translationKey: "userPreferences.displaySection" as ParseKeys, icon: LuMoon },
    // Future section for when notifications preferences are added
    // { id: "notifications", translationKey: "userPreferences.notificationsSection" as ParseKeys, icon: FaBell },
] as const;

// Exporting the section IDs as a type for use in other components (e.g., UserPreferencesView)
export type PreferencesSectionId = (typeof PREFERENCES_SECTIONS)[number]["id"];

interface PreferencesNavProps {
    activeSection: PreferencesSectionId;
    onSectionChange: (sectionId: PreferencesSectionId) => void;
}

/**
 * Navigation menu for user preferences sections.
 * Provides sidebar-style navigation matching the app's main sidebar pattern.
 *
 * @example
 * ```tsx
 * const [activeSection, setActiveSection] = useState("account");
 *
 * <PreferencesNav
 *   activeSection={activeSection}
 *   onSectionChange={setActiveSection}
 * />
 * ```
 */
export function PreferencesNav({ activeSection, onSectionChange }: PreferencesNavProps) {
    const { t } = useTranslation();

    return (
        <VStack
            as="nav"
            gap="1"
            width="240px"
            align="stretch"
            aria-label={t("userPreferences.navigation", { defaultValue: "Preferences navigation" })}
        >
            {PREFERENCES_SECTIONS.map((section) => {
                const isActive = activeSection === section.id;
                const Icon = section.icon;

                return (
                    // Using Box as="button" instead of Button to maintain custom styling without variant complexity.
                    // Workaround: Chakra Box with as="button" doesn't infer button-specific props in TypeScript.
                    <Box
                        key={section.id}
                        as="button"
                        display="flex"
                        alignItems="center"
                        gap="3"
                        px="4"
                        py="3"
                        borderRadius="md"
                        cursor="pointer"
                        transition="all 0.2s"
                        bg={isActive ? "bg.elevated" : "transparent"}
                        _hover={{
                            bg: "bg.muted",
                        }}
                        onClick={() => onSectionChange(section.id)}
                        onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                                onSectionChange(section.id);
                            }
                        }}
                        aria-current={isActive ? "location" : undefined}
                    >
                        <Flex
                            as="span"
                            alignItems="center"
                            justifyContent="center"
                            minWidth="20px"
                            color={isActive ? "icon.active" : "fg.muted"}
                            filter={isActive ? "drop-shadow(0px 0px 4px currentColor)" : undefined}
                        >
                            <Icon size={20} />
                        </Flex>
                        <Text
                            fontSize="sm"
                            fontWeight={isActive ? "semibold" : "medium"}
                            color={isActive ? "fg.default" : "fg.muted"}
                            whiteSpace="nowrap"
                        >
                            {t(section.translationKey)}
                        </Text>
                    </Box>
                );
            })}
        </VStack>
    );
}
