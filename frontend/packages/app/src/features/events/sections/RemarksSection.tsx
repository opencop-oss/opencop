// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Center, Flex, Skeleton, Text, VStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import type { EventDetails } from "@opencop/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { eventMutations, eventQueries } from "../events.queries";
import { RemarkCard } from "../detail/RemarkCard";
import { CreateRemark } from "../detail/CreateRemark";
import { useSpinDelay } from "spin-delay";
import { Divider } from "@opencop/ui";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";

interface RemarksSectionProps {
    details: EventDetails;
}

export function RemarksSection({ details }: RemarksSectionProps) {
    const { t } = useTranslation();
    const client = useQueryClient();
    const sourceId = details.source;
    const {
        data: remarks,
        isLoading,
        isError,
    } = useQuery({
        ...eventQueries.remarks(sourceId, details.objectId),
        enabled: sourceId !== "local",
    });
    const { mutateAsync: addRemark } = useMutation(
        eventMutations.remarks.add(client, sourceId, details.objectId),
    );
    const { data: layerRole, isLoading: layersLoading } = useQuery({
        ...layerQueries.myList(),
        select: (layers) => layers.find((info) => info.layerId === details.source)?.role,
    });
    const { data: sourceRole, isLoading: sourcesLoading } = useQuery({
        ...dataSourceQueries.myList(),
        select: (sources) => sources.find((info) => info.serverId === details.source)?.role,
    });
    const loading = isLoading || layersLoading || sourcesLoading;
    const showSkeleton = useSpinDelay(isLoading, {
        delay: 500,
        minDuration: 200,
        ssr: false,
    });

    const isEditor =
        layerRole === "EDITOR" ||
        layerRole === "ADMIN" ||
        layerRole === "OWNER" ||
        sourceRole === "ADMIN" ||
        sourceRole === "OWNER";

    if (sourceId === "local") {
        return (
            <Center w="full" h="full">
                <Text fontStyle="italic" textStyle="sm">
                    {t("events.broadcastToAddRemark")}
                </Text>
            </Center>
        );
    }

    if (loading && showSkeleton) {
        return (
            <VStack w="full" h="full">
                {Array.from({ length: 3 }).map((_, i) => (
                    // eslint-disable-next-line @eslint-react/no-array-index-key
                    <Skeleton key={i} w="full" h="32" />
                ))}
            </VStack>
        );
    }

    if (isError) {
        return (
            <Center w="full" h="full">
                <Text color="red.400" fontStyle="italic" textStyle="sm">
                    {t("events.errorFetchingRemarks")}
                </Text>
            </Center>
        );
    }

    return (
        remarks && (
            <Flex direction="column" gap="4" py={3} height="100%">
                {isEditor && (
                    <>
                        <CreateRemark addRemark={addRemark} />
                        <Divider />
                    </>
                )}
                <VStack w="full" h="full" overflowY="auto">
                    {remarks.map((remark) => (
                        <RemarkCard key={remark.id} remark={remark} sourceId={sourceId} />
                    ))}
                </VStack>
            </Flex>
        )
    );
}
