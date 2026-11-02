// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Center, Icon, Popover, Portal, VStack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Tooltip } from "@opencop/ui";
import { SideBarWrapper } from "./SideBarWrapper";
import { ModuleIndicator } from "./ModuleIndicator";
import { ModuleButton } from "./ModuleButton";
import { useSidebar } from "./SidebarContext";
import { TbLogout } from "react-icons/tb";
import { FaGlobe, FaUser } from "react-icons/fa6";
import { ProfileModuleButton } from "./ProfileModuleButton";
import { startCase } from "lodash";
import { Logo } from "@opencop/ui";
import { preferencesMutations } from "~/features/preferences/preferences.queries";

const LANGUAGES = [
    { labelKey: "language.english", code: "en" },
    { labelKey: "language.mandarin", code: "tw" },
    { labelKey: "language.french", code: "fr" },
] as const;

export function SideBar() {
    const { modules } = useSidebar();
    const { t, i18n } = useTranslation();
    const [langOpen, setLangOpen] = useState(false);
    const queryClient = useQueryClient();

    const { mutate: updatePreferencesMutate } = useMutation(
        preferencesMutations.update(queryClient),
    );

    const userModule = { name: "user", icon: <FaUser /> };
    const logoutModule = { name: "logout", icon: <TbLogout /> };

    const handleLanguageChange = (code: string) => {
        i18n.changeLanguage(code);
        localStorage.setItem("language", code);
        updatePreferencesMutate({ locale: code });
        setLangOpen(false);
    };

    return (
        <SideBarWrapper>
            {/* Logo+Modules Wrapper */}
            <Box w="full">
                <Center mb="5" mt="5" flexDirection="column">
                    <Logo width={24} height={24} />
                </Center>

                <VStack pos="relative" gap="0">
                    <ModuleIndicator />
                    {modules.map((m) => (
                        <Tooltip
                            key={m.name}
                            content={t(`sidebar.${m.name}`, {
                                defaultValue: startCase(m.name),
                            })}
                        >
                            <ModuleButton module={m} />
                        </Tooltip>
                    ))}
                </VStack>
            </Box>

            {/* Toggles + Profile — bottom of sidebar */}
            <VStack w="full">
                {/* Language Selector */}
                <Popover.Root open={langOpen} onOpenChange={(e) => setLangOpen(e.open)}>
                    <Tooltip content={t("navigation.language")} disabled={langOpen}>
                        <Popover.Trigger asChild>
                            <Button
                                padding={2}
                                w="full"
                                h="16"
                                flexDirection="column"
                                alignItems="center"
                                justifyItems="center"
                                opacity={1}
                                variant="subtle"
                                borderRadius="xs"
                                gap="1"
                                bg="transparent"
                                color={langOpen ? "icon.active" : "fg"}
                                _hover={{ bg: "bg.interactive" }}
                            >
                                <Icon
                                    w="8"
                                    color={langOpen ? "icon.active" : "#64758B"}
                                    filter={
                                        langOpen
                                            ? "drop-shadow(1px 1px 4px currentColor)"
                                            : undefined
                                    }
                                >
                                    <FaGlobe />
                                </Icon>
                            </Button>
                        </Popover.Trigger>
                    </Tooltip>
                    <Portal>
                        <Popover.Positioner>
                            <Popover.Content
                                bg="bg.panel"
                                backdropFilter="blur(4px)"
                                borderColor="border"
                                borderWidth="1px"
                                p="1"
                                w="auto"
                                minW="140px"
                            >
                                <VStack gap="0" align="stretch">
                                    {LANGUAGES.map((lang) => (
                                        <Button
                                            key={lang.code}
                                            variant="ghost"
                                            colorPalette="brand"
                                            size="sm"
                                            justifyContent="flex-start"
                                            fontWeight={
                                                i18n.language === lang.code ? "bold" : "normal"
                                            }
                                            color={
                                                i18n.language === lang.code ? "icon.active" : "fg"
                                            }
                                            onClick={() => handleLanguageChange(lang.code)}
                                        >
                                            {t(lang.labelKey)}
                                        </Button>
                                    ))}
                                </VStack>
                            </Popover.Content>
                        </Popover.Positioner>
                    </Portal>
                </Popover.Root>

                <Tooltip
                    content={t("navigation.userPreferences", { defaultValue: "User Preferences" })}
                >
                    <ProfileModuleButton m={userModule} />
                </Tooltip>

                <Tooltip content={t("navigation.logout")}>
                    <ProfileModuleButton m={logoutModule} />
                </Tooltip>
            </VStack>
        </SideBarWrapper>
    );
}
