// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    FaFile,
    FaFileAudio,
    FaFileExcel,
    FaFilePdf,
    FaFilePowerpoint,
    FaFileWord,
    FaImage,
    FaVideo,
} from "react-icons/fa";

export function FileTypeIcon({
    mimeType,
    contentType,
    size = 28,
}: {
    mimeType?: string | null;
    contentType?: string | null;
    size?: number;
}) {
    if (contentType === "IMAGE") return <FaImage size={size} />;
    if (contentType === "VIDEO") return <FaVideo size={size} />;
    if (!mimeType) return <FaFile size={size} />;
    if (mimeType === "application/pdf") return <FaFilePdf size={size} />;
    if (mimeType.startsWith("audio/")) return <FaFileAudio size={size} />;
    if (mimeType.includes("word")) return <FaFileWord size={size} />;
    if (mimeType.includes("excel") || mimeType.includes("spreadsheet"))
        return <FaFileExcel size={size} />;
    if (mimeType.includes("powerpoint") || mimeType.includes("presentation"))
        return <FaFilePowerpoint size={size} />;
    return <FaFile size={size} />;
}
