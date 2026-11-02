// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.usecase.media.StreamPlaybackReadiness;
import com.opencop.core.api.general.ports.gateway.media.MediaStreamGateway;
import com.opencop.core.api.general.technical.gateway.mediamtx.MediaMtxGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaMtxConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(MediaMtxConfig.class);

  @Test
  void mediaStreamGateway_whenDisabled_usesUnavailableFallbackEvenWithStaleUrls() {
    contextRunner
        .withPropertyValues(
            "opencop.mediamtx.enabled=false",
            "opencop.mediamtx.api.base-url=http://stale-mediamtx:9997",
            "opencop.mediamtx.hls.public-base-url=http://stale.example.test/streams")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MediaStreamGateway.class);
              assertThat(context).hasSingleBean(StreamPlaybackReadiness.class);
              assertThat(context).doesNotHaveBean(MediaMtxGateway.class);
              assertThat(context.getBean(StreamPlaybackReadiness.class).ready()).isFalse();
              assertThat(
                      context
                          .getBean(MediaStreamGateway.class)
                          .prepareStream(
                              "att_6d063523-0461-439e-b025-ed3baf3b1d18", "rtsp://camera/stream"))
                  .isEmpty();
            });
  }

  @Test
  void mediaStreamGateway_whenEnabled_usesMediaMtxGateway() {
    contextRunner
        .withPropertyValues(
            "opencop.mediamtx.enabled=true",
            "opencop.mediamtx.api.base-url=http://localhost:9997",
            "opencop.mediamtx.hls.public-base-url=/streams")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MediaStreamGateway.class);
              assertThat(context).hasSingleBean(StreamPlaybackReadiness.class);
              assertThat(context).hasSingleBean(MediaMtxGateway.class);
              assertThat(context.getBean(StreamPlaybackReadiness.class).ready()).isTrue();
            });
  }

  @Test
  void streamPlaybackReadiness_whenEnabledWithoutPublicHlsBaseUrl_isNotReady() {
    contextRunner
        .withPropertyValues(
            "opencop.mediamtx.enabled=true",
            "opencop.mediamtx.api.base-url=http://localhost:9997",
            "opencop.mediamtx.hls.public-base-url= ")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StreamPlaybackReadiness.class);
              assertThat(context.getBean(StreamPlaybackReadiness.class).ready()).isFalse();
            });
  }

  @Test
  void mediaStreamGateway_whenEnabledWithoutApiBaseUrl_failsStartup() {
    contextRunner
        .withPropertyValues(
            "opencop.mediamtx.enabled=true",
            "opencop.mediamtx.api.base-url= ",
            "opencop.mediamtx.hls.public-base-url=/streams")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining(
                      "opencop.mediamtx.api.base-url must be configured when MediaMTX is enabled");
            });
  }
}
