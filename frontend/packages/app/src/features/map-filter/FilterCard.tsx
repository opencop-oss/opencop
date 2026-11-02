// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Card, HStack, IconButton } from "@chakra-ui/react";
import { useMapFilterStore, type MapFilter } from "./map-filter.store";
import { useTranslation } from "react-i18next";
import { FaTrash } from "react-icons/fa";
import { useHistoricalMode } from "../historical/data-plane/historical.store";
import { OcSwitch, Tooltip } from "@opencop/ui";

export type FilterCardProps = {
    activate: () => void;
    deactivate: () => void;
    remove: () => void;
    filter: MapFilter;
};

export function FilterCard({ filter, activate, deactivate, remove }: FilterCardProps) {
    const { t } = useTranslation();
    const activeNames = useMapFilterStore((s) => s.activeNames);
    const isActive = activeNames.includes(filter.name);
    const isHistorical = useHistoricalMode() === "historical";

    return (
        <Card.Root size="sm" bg="slate.elevated" w="full">
            <Card.Body>
                <HStack justify="space-between">
                    <HStack>
                        <Card.Title>{filter.name}</Card.Title>

                        <Badge>
                            {filter.combine === "ALL" ? t("filter.all") : t("filter.any")}
                        </Badge>
                    </HStack>
                    <HStack>
                        <Tooltip
                            content={t("filter.unavailableInHistory")}
                            uppercase={false}
                            disabled={!isHistorical}
                        >
                            <OcSwitch
                                checked={isActive}
                                label={t("filter.activateNamed", { name: filter.name })}
                                srOnlyLabel
                                disabled={isHistorical}
                                onCheckedChange={(checked) => (checked ? activate() : deactivate())}
                                fullWidth={false}
                                rootProps={{ size: "sm" }}
                            />
                        </Tooltip>
                        <Tooltip
                            content={t("filter.unavailableInHistory")}
                            uppercase={false}
                            disabled={!isHistorical}
                        >
                            <IconButton
                                onClick={remove}
                                disabled={isHistorical}
                                size="2xs"
                                colorPalette="red"
                                variant="ghost"
                                aria-label={t("filter.removeFilter")}
                            >
                                <FaTrash />
                            </IconButton>
                        </Tooltip>
                    </HStack>
                </HStack>
            </Card.Body>
        </Card.Root>
    );
}
