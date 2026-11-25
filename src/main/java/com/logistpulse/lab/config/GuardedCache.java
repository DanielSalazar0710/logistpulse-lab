package com.logistpulse.lab.config;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.cache.Cache;

/**
 * Parte 8.3 · "Cortacircuitos" mínimo alrededor de la caché de rutas.
 *
 * Con Redis apagado, cada operación tardaba varios segundos (el nombre "redis" deja de resolverse en el DNS de Docker
 * y la resolución espera su timeout) aunque el error luego se tratara como cache-miss. Tras el primer fallo el
 * circuito se abre durante {@link #OPEN_FOR}: mientras tanto la caché se comporta como vacía, sin tocar la red.
 * Pasado ese tiempo la siguiente petición vuelve a probar Redis (semiabierto) y, si responde, todo sigue normal.
 */
final class GuardedCache implements Cache {

    static final Duration OPEN_FOR = Duration.ofSeconds(15);
    private static final AtomicLong openUntil = new AtomicLong();

    private final Cache delegate;

    GuardedCache(Cache delegate) {
        this.delegate = delegate;
    }

    static boolean isOpen() {
        return System.currentTimeMillis() < openUntil.get();
    }

    private static RuntimeException trip(RuntimeException e) {
        openUntil.set(System.currentTimeMillis() + OPEN_FOR.toMillis());
        return e;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        if (isOpen()) {
            return null;
        }
        try {
            return delegate.get(key);
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        if (isOpen()) {
            return null;
        }
        try {
            return delegate.get(key, type);
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        if (isOpen()) {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }
        try {
            return delegate.get(key, valueLoader);
        } catch (ValueRetrievalException e) {
            throw e;
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (isOpen()) {
            return;
        }
        try {
            delegate.put(key, value);
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }

    @Override
    public void evict(Object key) {
        if (isOpen()) {
            return;
        }
        try {
            delegate.evict(key);
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }

    @Override
    public void clear() {
        if (isOpen()) {
            return;
        }
        try {
            delegate.clear();
        } catch (RuntimeException e) {
            throw trip(e);
        }
    }
}
