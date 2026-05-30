package com.metax.service;

import com.metax.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * CacheExampleService .
 *
 * <p>
 * Spring Cache 示例服务，用于展示 Redisson Cache 的 @Cacheable / @CachePut / @CacheEvict 推荐写法
 * 示例不承载真实业务流程，接入业务时应替换为实际 Repository 或外部服务调用
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/30
 */
@Service
public class CacheExampleService {

    /**
     * 查询 provider 配置示例
     *
     * <p>
     * 默认配置查询场景，绑定 providerConfig 缓存，key 使用 provider 类型隔离不同模型提供商
     *
     * @param provider provider 类型
     * @return provider 配置摘要
     */
    @Cacheable(cacheNames = CacheConfig.PROVIDER_CONFIG_CACHE, key = "#provider")
    public String getProviderConfig(String provider) {
        return "provider = " + provider;
    }

    /**
     * 更新 provider 配置示例
     *
     * <p>
     * 配置刷新场景，绑定 providerConfig 缓存，方法返回值会覆盖同 provider 的缓存内容
     *
     * @param provider provider 类型
     * @param config provider 配置摘要
     * @return provider 配置摘要
     */
    @CachePut(cacheNames = CacheConfig.PROVIDER_CONFIG_CACHE, key = "#provider")
    public String updateProviderConfig(String provider, String config) {
        return config;
    }

    /**
     * 清理 provider 配置缓存示例
     *
     * <p>
     * 配置失效场景，绑定 providerConfig 缓存，用于删除指定 provider 的缓存内容
     *
     * @param provider provider 类型
     */
    @CacheEvict(cacheNames = CacheConfig.PROVIDER_CONFIG_CACHE, key = "#provider")
    public void evictProviderConfig(String provider) {
    }
}
