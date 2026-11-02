// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.policy.media.StreamTransportPolicy;
import com.opencop.core.api.general.business.usecase.media.DeleteMediaUsecase;
import com.opencop.core.api.general.business.usecase.media.ListMediaUsecase;
import com.opencop.core.api.general.business.usecase.media.RegisterMediaUrlUsecase;
import com.opencop.core.api.general.business.usecase.media.RetrieveMediaUsecase;
import com.opencop.core.api.general.business.usecase.media.ServeLayerIconUsecase;
import com.opencop.core.api.general.business.usecase.media.UploadMediaUsecase;
import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.ListMedia;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.ServeLayerIcon;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.ports.usecase.media.RegisterMediaUrl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaConfig {

  @Bean
  UploadMedia uploadMedia(MediaRepository mediaRepository, MediaStore mediaStore) {
    return new UploadMediaUsecase(mediaRepository, mediaStore);
  }

  @Bean
  RetrieveMedia retrieveMedia(MediaRepository mediaRepository, MediaStore mediaStore) {
    return new RetrieveMediaUsecase(mediaRepository, mediaStore);
  }

  @Bean
  ListMedia listMedia(MediaRepository mediaRepository) {
    return new ListMediaUsecase(mediaRepository);
  }

  @Bean
  DeleteMedia deleteMedia(MediaRepository mediaRepository, MediaStore mediaStore) {
    return new DeleteMediaUsecase(mediaRepository, mediaStore);
  }

  @Bean
  ServeLayerIcon serveLayerIcon(MediaRepository mediaRepository, MediaStore mediaStore) {
    return new ServeLayerIconUsecase(mediaRepository, mediaStore);
  }

  @Bean
  RegisterMediaUrl registerMediaUrl(
      MediaUrlRepository mediaUrlRepository, StreamTransportPolicy streamTransportPolicy) {
    return new RegisterMediaUrlUsecase(mediaUrlRepository, streamTransportPolicy);
  }
}
