// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Heading, Spinner, Text } from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import { Fragment, useState } from "react";
import { FaFolder, FaFolderOpen } from "react-icons/fa6";
import { useTranslation } from "react-i18next";
import { layerQueries } from "~/features/layers/layers.queries";
// import ServerEntry from "./ServerEntry";
import { TreeView } from "@opencop/ui";

interface LayerEntryProps {
    layer: {
        id: string;
        name: string;
    };
}

interface Server {
    server?: Server; // In case the server data is wrapped
    id?: string;
    serverId?: string;
    server_id?: string;
    name?: string;
    serverName?: string;
}

export function LayerEntry({ layer }: LayerEntryProps) {
    const { t } = useTranslation();
    const [open, setOpen] = useState<boolean>(false);

    const {
        data: serversData = [],
        isLoading: serversLoading,
        error: serversError,
    } = useQuery(layerQueries.dataSources(layer.id)) as {
        data: Server[];
        isLoading: boolean;
        error: Error;
    };

    const servers: Server[] = serversData.map((item: Server) => item.server ?? item);

    const handleToggle = (e: React.MouseEvent) => {
        e.stopPropagation();
        setOpen(!open);
    };

    return (
        <Box width="100%">
            <Flex
                width="100%"
                direction="row"
                p={2}
                transition="background-color .2s ease-out"
                _hover={{ bg: "glass.subtle" }}
                borderRadius="md"
            >
                <Flex
                    direction="row"
                    alignItems="center"
                    flexGrow={1}
                    gap={2}
                    onClick={handleToggle}
                    cursor="pointer"
                >
                    {open ? <FaFolderOpen size={12} /> : <FaFolder size={12} />}
                    <Heading size="xs" fontWeight={600}>
                        {layer.name || layer.id} {t("filters.servers")}
                    </Heading>
                    {!serversLoading && (
                        <Text fontSize="xs" fontWeight={400} opacity={0.7}>
                            ({servers.length})
                        </Text>
                    )}
                </Flex>
            </Flex>
            {open ? (
                <TreeView variant="trunk">
                    {serversError ? (
                        <Flex p={2}>
                            <Text fontSize="xs" color="red.400" fontStyle="italic">
                                {t("filters.errorLoadingServers")}
                            </Text>
                        </Flex>
                    ) : serversLoading ? (
                        <Flex p={2} direction="row" alignItems="center" gap={2}>
                            <Spinner size="xs" />
                            <Heading size="xs" fontWeight={400} fontStyle="italic">
                                {t("filters.loadingServers")}
                            </Heading>
                        </Flex>
                    ) : servers.length > 0 ? (
                        servers.map((server) => (
                            // TODO: restore ServerEntry when component is ready
                            <Fragment key={server.serverId} />
                        ))
                    ) : (
                        <Flex p={2}>
                            <Heading size="xs" fontWeight={400} fontStyle="italic">
                                {t("filters.noServers")}
                            </Heading>
                        </Flex>
                    )}
                </TreeView>
            ) : null}
        </Box>
    );
}
