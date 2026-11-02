// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { FaUser, FaUsers } from "react-icons/fa";
import { Link } from "react-router";
import { ResourceTypeBadge } from "@opencop/ui";
import type { GenericItem } from "./MembershipDialog";

interface MemberRowProps<T extends GenericItem> {
    item: T;
    readOnly: boolean;
    onGroupDeleteClick?: (item: T) => void;
    onRevokeClick: (item: T) => void;
}

export function MemberRow<T extends GenericItem>({
    item,
    readOnly,
    onGroupDeleteClick,
    onRevokeClick,
}: MemberRowProps<T>) {
    const { t } = useTranslation();
    const isGroup = item.kind === "group";
    const showRevoke = !readOnly && (!isGroup || !!onGroupDeleteClick);

    return (
        <Box p={3} borderWidth="1px" borderRadius="md" borderColor="table.border">
            <Flex align="center" gap={3}>
                {/* Icon */}
                <Flex
                    alignItems="center"
                    justifyContent="center"
                    w="44px"
                    h="44px"
                    flexShrink={0}
                    bg="bg.elevatedLight"
                    borderRadius="md"
                    border="1px solid"
                    borderColor="border.muted"
                >
                    {isGroup ? (
                        <FaUsers color="#95A3B8" size={18} />
                    ) : (
                        <FaUser color="#95A3B8" size={18} />
                    )}
                </Flex>

                {/* Name + badges */}
                <Flex direction="column" flex={1} minW={0} gap="4px">
                    <Text fontSize="sm" fontWeight="medium" wordBreak="break-word">
                        {item.label}
                    </Text>
                    <Flex gap={2} align="center" wrap="wrap">
                        {item.type && (
                            <ResourceTypeBadge
                                label={t(`roles.${item.type.toLowerCase()}` as never, {
                                    defaultValue: item.type,
                                })}
                                colorPalette={item.typeColorPalette ?? "cyan"}
                            />
                        )}
                        {item.accessType === "group" && (
                            <ResourceTypeBadge
                                label={t("adminDialogs.accessTypeGroup")}
                                colorPalette="purple"
                            />
                        )}
                    </Flex>
                </Flex>

                {/* Actions */}
                <Flex gap={2} align="center" flexShrink={0}>
                    {isGroup && item.adminEditHref && (
                        <Link to={item.adminEditHref}>
                            <Button
                                size="sm"
                                fontSize="10px"
                                fontWeight="700"
                                letterSpacing="0.1em"
                                variant="subtle"
                                borderColor="rgb(79 209 237 / 0.3)"
                                borderWidth="0.1px"
                                color="rgb(79 209 237 / 0.8)"
                                bg="transparent"
                                _hover={{ bg: "rgb(79 209 237 / 0.08)" }}
                                textTransform="uppercase"
                            >
                                {t("adminGroups.editGroup")}
                            </Button>
                        </Link>
                    )}
                    {showRevoke && (
                        <Button
                            size="sm"
                            fontSize="10px"
                            fontWeight="700"
                            letterSpacing="0.1em"
                            onClick={() => onRevokeClick(item)}
                            variant="subtle"
                            borderColor="rgb(194 109 109 / 0.3)"
                            borderWidth="0.1px"
                            color="rgb(194 109 109 / 0.8)"
                            bg="transparent"
                            _hover={{ bg: "#A660631a" }}
                            textTransform="uppercase"
                        >
                            {t("adminDialogs.revokeAccess")}
                        </Button>
                    )}
                </Flex>
            </Flex>
        </Box>
    );
}
