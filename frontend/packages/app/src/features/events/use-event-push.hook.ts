// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMutation } from "@tanstack/react-query";
import { eventMutations } from "./events.queries";
import { useEventDetails } from "./use-event-details.hook";
import { useCallback } from "react";

export function useEventPush(): (id: string) => Promise<void> {
    const { getLatestGeojson } = useEventDetails();
    const { mutateAsync } = useMutation(eventMutations.push());

    return useCallback(
        async (id: string) => {
            const geojson = getLatestGeojson();
            if (!geojson) {
                throw new Error("No changes to save");
            }

            const sourceFeature = {
                ...geojson,
                properties: {
                    opencop: {
                        ...geojson.properties.opencop,
                        source: id,
                    },
                },
            };

            await mutateAsync({ sourceId: id, feature: sourceFeature });
        },
        [getLatestGeojson, mutateAsync],
    );
}
