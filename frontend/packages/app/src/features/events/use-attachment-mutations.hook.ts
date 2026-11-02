// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { StreamKind } from "@opencop/api";
import { eventMutations } from "./events.queries";

interface UseAttachmentMutationsProps {
    sourceId: string;
    objectId: string;
}

export function useAttachmentMutations({ sourceId, objectId }: UseAttachmentMutationsProps) {
    const queryClient = useQueryClient();

    const { mutateAsync: uploadAsync } = useMutation(
        eventMutations.attachments.upload(queryClient),
    );
    const { mutateAsync: registerUrlAsync, isPending: isRegisteringUrl } = useMutation(
        eventMutations.attachments.registerUrl(queryClient),
    );
    const { mutate: deleteMutate } = useMutation(eventMutations.attachments.delete(queryClient));

    const uploadAttachment = useCallback(
        (file: File) => uploadAsync({ sourceId, objectId, file }),
        [uploadAsync, sourceId, objectId],
    );

    const registerUrl = useCallback(
        (url: string, streamKind: StreamKind) =>
            registerUrlAsync({ sourceId, objectId, url, streamKind }),
        [registerUrlAsync, sourceId, objectId],
    );

    const deleteAttachment = useCallback(
        (mediaId: string) => deleteMutate({ sourceId, objectId, mediaId }),
        [deleteMutate, sourceId, objectId],
    );

    return { uploadAttachment, registerUrl, isRegisteringUrl, deleteAttachment };
}
