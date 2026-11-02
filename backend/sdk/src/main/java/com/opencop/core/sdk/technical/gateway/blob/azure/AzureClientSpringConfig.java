// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.blob.azure;

import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.media.azure.AzureBlobMediaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@EnableConfigurationProperties(AzureProperties.class)
@ConditionalOnProperty(name = "opencop.cloud.provider", havingValue = "azure")
public class AzureClientSpringConfig {

  private static final Logger log = LoggerFactory.getLogger(AzureClientSpringConfig.class);

  @Bean
  @Conditional(BeanCondition.class)
  public BlobServiceClient blobServiceClient(final AzureProperties azureProperties) {
    return new BlobServiceClientBuilder()
        .endpoint(azureProperties.getEndpoint())
        .credential(
            new StorageSharedKeyCredential(
                azureProperties.getAccountName(), azureProperties.getAccountKey()))
        .httpClient(new JdkHttpClientBuilder().build())
        .buildClient();
  }

  @Bean("azure")
  @Conditional(BeanCondition.class)
  public BlobClient azureClient(
      final AzureProperties azureProperties, final BlobServiceClient blobServiceClient) {
    return new AzureBlobStorageClient(azureProperties.getContainerName(), blobServiceClient);
  }

  @Bean
  @Conditional(BeanCondition.class)
  public MediaStore mediaStore(
      final AzureProperties azureProperties, final BlobServiceClient blobServiceClient) {
    var media = azureProperties.getMedia();
    return new AzureBlobMediaStore(
        blobServiceClient,
        media.getContainerName(),
        media.getSasExpiryMinutes(),
        azureProperties.getEndpoint(),
        azureProperties.getPublicEndpoint());
  }

  public static class BeanCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      Environment env = context.getEnvironment();
      return env.containsProperty("opencop.blob.container-name")
          && env.containsProperty("opencop.blob.account-name")
          && env.containsProperty("opencop.blob.account-key")
          && env.containsProperty("opencop.blob.endpoint");
    }
  }
}
