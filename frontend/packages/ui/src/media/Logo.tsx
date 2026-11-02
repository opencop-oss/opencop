// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import React from "react";

type LogoProps = React.ImgHTMLAttributes<HTMLImageElement>;

export function Logo(props: LogoProps) {
    const logoSrc: string = "/branding/logo.svg";
    return <img src={logoSrc} alt="" {...props} />;
}
