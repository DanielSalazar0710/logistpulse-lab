package com.logistpulse.lab.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Parte 8.3 (caos con Redis): la caché de rutas es una optimización, no una dependencia crítica.
 * Sin este manejador, con Redis caído cada GET /api/routes y cada despacho esperaban el timeout y respondían 500.
 * Ahora un error de la caché se registra, se cuenta en logistpulse_cache_errors_total y se trata como un
 * cache-miss: la ruta se calcula con A* y la API sigue respondiendo (más lenta, pero disponible).
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final Counter errors;

    public CacheConfig(MeterRegistry registry) {
        this.errors = Counter.builder("logistpulse.cache.errors")
                .description("Errores de la caché de rutas tratados como cache-miss").register(registry);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                degrade("leer", cache, key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                degrade("guardar", cache, key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                degrade("invalidar", cache, key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                degrade("vaciar", cache, "*", e);
            }
        };
    }

    private void degrade(String op, Cache cache, Object key, RuntimeException e) {
        errors.increment();
        log.warn("Caché '{}' no disponible al {} {}: se calcula sin caché ({})",
                cache.getName(), op, key, e.getClass().getSimpleName());
    }
}
