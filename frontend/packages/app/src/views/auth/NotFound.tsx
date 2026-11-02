// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FaSearchMinus } from "react-icons/fa";
import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { FullPageMessage } from "~/features/errors/FullPageMessage";

const RETURN_ROUTE = "/map";

export function NotFound() {
    const { t } = useTranslation();
    const navigate = useNavigate();

    return (
        <FullPageMessage
            icon={FaSearchMinus}
            title={t("pages.notFound")}
            message={t("pages.notFoundMessage")}
            buttonLabel={t("pages.returnToMap")}
            onButtonClick={() => navigate(RETURN_ROUTE)}
        />
    );
}
