// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.usecase.integration.mapping.egress.EgressMapperAdmissionUsecase;
import com.opencop.core.sdk.business.usecase.integration.mapping.ingress.IngressMapperAdmissionUsecase;
import com.opencop.core.sdk.ports.integration.mapping.EgressMappingContribution;
import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers admission infrastructure and shared contribution views exactly once, independently of
 * which provider registration classes are discovered for supported capabilities.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationCapabilityInfrastructureConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(IntegrationCapabilityInfrastructureConfiguration.class);

  @Bean
  IngressMapperAdmissionUsecase ingressMapperAdmissionUsecase() {
    return new IngressMapperAdmissionUsecase();
  }

  @Bean
  IngressMapperContributions ingressMapperContributions(
      ObjectProvider<IngressMappingContribution> contributions,
      IngressMapperAdmissionUsecase admissionUsecase) {
    var admission = admissionUsecase.admit(contributions.orderedStream().toList());
    admission
        .rejections()
        .forEach(IntegrationCapabilityInfrastructureConfiguration::logIngressRejection);
    return admission.contributions();
  }

  @Bean
  EgressMapperAdmissionUsecase egressMapperAdmissionUsecase() {
    return new EgressMapperAdmissionUsecase();
  }

  @Bean
  EgressMapperContributions egressMapperContributions(
      ObjectProvider<EgressMappingContribution> contributions,
      EgressMapperAdmissionUsecase admissionUsecase) {
    var admission = admissionUsecase.admit(contributions.orderedStream().toList());
    admission
        .rejections()
        .forEach(IntegrationCapabilityInfrastructureConfiguration::logEgressRejection);
    return admission.contributions();
  }

  private static void logEgressRejection(EgressMapperContributionDecision rejection) {
    var contribution = rejection.contribution();
    var reason = rejection.rejectionReason().orElseThrow();
    log.warn(
        "Rejected egress mapper contribution reason={} mapperId={} mapperName='{}' mapperVersion={} implementation={}",
        reason.code(),
        contribution.id(),
        contribution.name(),
        contribution.version(),
        contribution.getClass().getName());
  }

  private static void logIngressRejection(IngressMapperContributionDecision rejection) {
    var contribution = rejection.contribution();
    var reason = rejection.rejectionReason().orElseThrow();
    log.warn(
        "Rejected ingress mapper contribution reason={} mapperId={} mapperName='{}' mapperVersion={} implementation={}",
        reason.code(),
        contribution.id(),
        contribution.name(),
        contribution.version(),
        contribution.getClass().getName());
  }

  @Bean
  static IntegrationCapabilityPolicyBeanFactoryPostProcessor
      integrationCapabilityPolicyBeanFactoryPostProcessor() {
    return new IntegrationCapabilityPolicyBeanFactoryPostProcessor();
  }
}
