// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability;
import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapabilityCatalog;
import com.opencop.core.sdk.business.model.integration.capability.IntegrationCapabilityPolicy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.TreeSet;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;

/**
 * Publishes the aggregate host policy and rejects statically visible contribution types from the
 * complete context before singleton creation. Type queries disable eager initialization; unknown
 * and unrelated bean types remain untouched. This trusted-startup configuration guard is not an
 * arbitrary-code sandbox.
 */
final class IntegrationCapabilityPolicyBeanFactoryPostProcessor
    implements BeanFactoryPostProcessor {

  static final String POLICY_BEAN_NAME = IntegrationCapabilityPolicy.class.getName();
  private static final IntegrationCapabilityCatalog CAPABILITY_CATALOG =
      IntegrationCapabilityCatalog.standard();

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    var declaredSupported = EnumSet.noneOf(IntegrationCapability.class);
    var rejected = EnumSet.noneOf(IntegrationCapability.class);
    for (var beanName : beanFactory.getBeanDefinitionNames()) {
      var declarations =
          beanFactory.findAllAnnotationsOnBean(
              beanName, EnableIntegrationCapabilities.class, false);
      for (var declaration : declarations) {
        declaredSupported.addAll(Arrays.asList(declaration.supported()));
        rejected.addAll(Arrays.asList(declaration.rejected()));
      }
    }

    var policy = IntegrationCapabilityPolicy.resolve(declaredSupported, rejected);
    beanFactory.registerSingleton(POLICY_BEAN_NAME, policy);

    var violations = new TreeSet<String>();
    for (var capability : policy.rejected()) {
      for (var contributionInterface :
          CAPABILITY_CATALOG.contractFor(capability).contributionInterfaces()) {
        for (var beanName : beanFactory.getBeanNamesForType(contributionInterface, true, false)) {
          var beanType = beanFactory.getType(beanName, false);
          violations.add(
              capability.name()
                  + " | "
                  + contributionInterface.getName()
                  + " | "
                  + beanName
                  + " | "
                  + (beanType == null ? "<unresolved>" : beanType.getName()));
        }
      }
    }

    if (!violations.isEmpty()) {
      throw new BeanDefinitionValidationException(
          "Beans implement contribution interfaces owned by rejected capabilities:\n"
              + String.join("\n", violations));
    }
  }
}
