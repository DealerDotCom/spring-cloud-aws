/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticspring.context.config.xml;

import com.google.code.ssm.CacheFactory;
import com.google.code.ssm.config.DefaultAddressProvider;
import com.google.code.ssm.providers.CacheConfiguration;
import com.google.code.ssm.providers.spymemcached.MemcacheClientFactoryImpl;
import com.google.code.ssm.spring.SSMCacheManager;
import org.elasticspring.context.cache.config.ElastiCacheAddressProvider;
import org.elasticspring.context.cache.config.SsmCacheFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Parser for the {@code <els-context:cache-manager />} element.
 *
 * @author Alain Sahli
 * @author Agim Emruli
 * @since 1.0
 */
public class CacheBeanDefinitionParser extends AbstractBeanDefinitionParser {

	private static final String CACHE_MANAGER = "cacheManager";
	private static final String CACHE_CLUSTER_ELEMENT_NAME = "cache-cluster";
	private static final String CACHE_REF_ELEMENT_NAME = "cache-ref";
	private static final String CACHE_ELEMENT_NAME = "cache";

	@Override
	protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		if (!parserContext.getRegistry().containsBeanDefinition(CACHE_MANAGER)) {
			BeanDefinitionBuilder cacheManagerDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SSMCacheManager.class);
			cacheManagerDefinitionBuilder.addPropertyValue("caches", createCacheCollection(element, parserContext));
			parserContext.getRegistry().registerBeanDefinition(CACHE_MANAGER, cacheManagerDefinitionBuilder.getBeanDefinition());
		} else {
			parserContext.getReaderContext().error("Only one cache manager can be defined", element);
		}
		return null;
	}

	private ManagedList<Object> createCacheCollection(Element element, ParserContext parserContext) {
		ManagedList<Object> caches = new ManagedList<Object>();
		List<Element> cacheElements = DomUtils.getChildElements(element);

		for (Element cacheElement : cacheElements) {
			String elementName = cacheElement.getLocalName();

			if (CACHE_REF_ELEMENT_NAME.equals(elementName)) {
				caches.add(new RuntimeBeanReference(cacheElement.getAttribute("ref")));
			} else if (CACHE_CLUSTER_ELEMENT_NAME.equals(elementName)) {
				int expiration = Integer.parseInt(getRequiredAttribute("expiration", cacheElement, parserContext));
				boolean allowClear = Boolean.TRUE.toString().equalsIgnoreCase(getRequiredAttribute("allowClear", cacheElement, parserContext));
				String cacheClusterId = getRequiredAttribute("cacheCluster", cacheElement, parserContext);
				caches.add(createSSMCache(cacheClusterId, createElastiCacheAddressProvider(parserContext.getRegistry(), cacheElement,
						cacheClusterId), expiration, allowClear));
			} else if (CACHE_ELEMENT_NAME.equals(elementName)) {
				String name = getRequiredAttribute("name", cacheElement, parserContext);
				String address = getRequiredAttribute("address", cacheElement, parserContext);
				int expiration = Integer.parseInt(getRequiredAttribute("expiration", cacheElement, parserContext));
				boolean allowClear = Boolean.TRUE.toString().equalsIgnoreCase(getRequiredAttribute("allowClear", cacheElement, parserContext));
				caches.add(createSSMCache(name, createDefaultAddressProvider(address), expiration, allowClear));
			}
		}
		return caches;
	}

	private BeanDefinition createSSMCache(String name, BeanDefinition addressProvider, int expiration, boolean allowClear) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SsmCacheFactoryBean.class);
		beanDefinitionBuilder.addConstructorArgValue(createCache(name, addressProvider));
		beanDefinitionBuilder.addConstructorArgValue(expiration);
		beanDefinitionBuilder.addPropertyValue("allowClear", allowClear);

		return beanDefinitionBuilder.getBeanDefinition();
	}

	private BeanDefinition createCache(String name, BeanDefinition addressProvider) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(CacheFactory.class);
		beanDefinitionBuilder.addPropertyValue("cacheName", name);
		beanDefinitionBuilder.addPropertyValue("cacheClientFactory", createClientFactoryImpl());
		beanDefinitionBuilder.addPropertyValue("addressProvider", addressProvider);
		beanDefinitionBuilder.addPropertyValue("configuration", createCacheConfiguration());

		return beanDefinitionBuilder.getBeanDefinition();
	}

	private AbstractBeanDefinition createCacheConfiguration() {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(CacheConfiguration.class);
		// TODO why do we set this flag to true? Shouldn't this be configurable?
		beanDefinitionBuilder.addPropertyValue("consistentHashing", true);
		return beanDefinitionBuilder.getBeanDefinition();
	}

	private BeanDefinition createDefaultAddressProvider(String address) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(DefaultAddressProvider.class);
		beanDefinitionBuilder.addConstructorArgValue(address);
		return beanDefinitionBuilder.getBeanDefinition();
	}

	private BeanDefinition createElastiCacheAddressProvider(BeanDefinitionRegistry beanDefinitionRegistry, Element source, String clusterId) {
		BeanDefinitionHolder elastiCacheClient = AmazonElastiCacheClientConfigurationUtils.registerElastiCacheClient(beanDefinitionRegistry, source);
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ElastiCacheAddressProvider.class);
		beanDefinitionBuilder.addConstructorArgReference(elastiCacheClient.getBeanName());
		beanDefinitionBuilder.addConstructorArgValue(clusterId);
		return beanDefinitionBuilder.getBeanDefinition();
	}

	private AbstractBeanDefinition createClientFactoryImpl() {
		return BeanDefinitionBuilder.rootBeanDefinition(MemcacheClientFactoryImpl.class).getBeanDefinition();
	}

	private static String getRequiredAttribute(String attributeName, Element source, ParserContext parserContext) {
		if (StringUtils.hasText(source.getAttribute(attributeName)) ) {
			return source.getAttribute(attributeName);
		}else{
			parserContext.getReaderContext().error("Attribute '" + attributeName + "' is required", source);
			return null;
		}
	}
}