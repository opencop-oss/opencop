// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.blob.BlobConfig;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureBlobStorageClient;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureClientSpringConfig;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureProperties;
import com.opencop.core.sdk.technical.gateway.media.azure.AzureBlobMediaStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

class CloudBlobConfigBindingTest {

  private static final String ACCOUNT_KEY =
      "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
  private static final String INTERNAL_ENDPOINT = "http://azurite:10000/devstoreaccount1";
  private static final String PUBLIC_ENDPOINT = "http://localhost:10000/devstoreaccount1";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                try {
                  var resource = new ClassPathResource("application.yml");
                  var loader = new YamlPropertySourceLoader();
                  for (var propertySource : loader.load("core-application", resource)) {
                    context.getEnvironment().getPropertySources().addLast(propertySource);
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              })
          .withUserConfiguration(BlobConfig.class, TestAzureClientSpringConfig.class)
          .withPropertyValues(
              "opencop.cloud.provider=azure",
              "cloud.blob.container-name=opencop-blob",
              "cloud.blob.account-name=devstoreaccount1",
              "cloud.blob.account-key=" + ACCOUNT_KEY,
              "cloud.blob.endpoint=" + INTERNAL_ENDPOINT);

  @Test
  void cloudBlobPublicEndpoint_bindsIntoAzureBeans() {
    contextRunner
        .withPropertyValues("cloud.blob.public-endpoint=" + PUBLIC_ENDPOINT)
        .run(
            context -> {
              assertThat(context).hasNotFailed();

              var properties = context.getBean(AzureProperties.class);
              assertThat(properties.getEndpoint()).isEqualTo(INTERNAL_ENDPOINT);
              assertThat(properties.getPublicEndpoint()).isEqualTo(PUBLIC_ENDPOINT);

              var blobClient = context.getBean(BlobClient.class);
              assertThat(blobClient).isInstanceOf(AzureBlobStorageClient.class);

              var mediaStore = context.getBean(MediaStore.class);
              assertThat(mediaStore).isInstanceOf(AzureBlobMediaStore.class);
              assertThat(ReflectionTestUtils.getField(mediaStore, "internalEndpoint"))
                  .isEqualTo(INTERNAL_ENDPOINT);
              assertThat(ReflectionTestUtils.getField(mediaStore, "publicEndpoint"))
                  .isEqualTo(PUBLIC_ENDPOINT);
            });
  }

  @Test
  void cloudBlobPublicEndpoint_whenMissing_defaultsBlankWithoutBlockingAzureBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();

          var properties = context.getBean(AzureProperties.class);
          assertThat(properties.getEndpoint()).isEqualTo(INTERNAL_ENDPOINT);
          assertThat(properties.getPublicEndpoint()).isEmpty();

          var blobClient = context.getBean(BlobClient.class);
          assertThat(blobClient).isInstanceOf(AzureBlobStorageClient.class);

          var mediaStore = context.getBean(MediaStore.class);
          assertThat(mediaStore).isInstanceOf(AzureBlobMediaStore.class);
          assertThat(ReflectionTestUtils.getField(mediaStore, "publicEndpoint")).isEqualTo("");
        });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AzureProperties.class)
  static class TestAzureClientSpringConfig extends AzureClientSpringConfig {

    @Bean
    @Override
    public BlobServiceClient blobServiceClient(AzureProperties azureProperties) {
      var blobServiceClient = mock(BlobServiceClient.class);
      var containerClient = mock(BlobContainerClient.class);
      when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);
      return blobServiceClient;
    }
  }
}
