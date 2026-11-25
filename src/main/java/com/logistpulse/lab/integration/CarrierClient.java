package com.logistpulse.lab.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del transportista externo (en el laboratorio lo simula WireMock).
 * Si el servicio falla o tarda más de 2 s, devuelve vacío y el llamador usa un ETA local (degradación elegante).
 *
 * Reto Parte 6.4: reintento con backoff exponencial (máx. 2 reintentos: 200 ms y 400 ms) SOLO para errores 5xx,
 * que suelen ser transitorios. No se reintenta:
 *   - un 4xx: es un error de la petición, repetirla da el mismo resultado;
 *   - un timeout o conexión rechazada: ya costó hasta 2 s; reintentar triplicaría la latencia del despacho
 *     y empujaría más carga a un servicio que ya está saturado.
 */
@Component
public class CarrierClient {

    private static final Logger log = LoggerFactory.getLogger(CarrierClient.class);

    static final int MAX_RETRIES = 2;
    static final Duration BASE_BACKOFF = Duration.ofMillis(200);

    public record CarrierEta(String code, int etaMinutes) {
    }

    private final RestClient client;
    private final Counter retries;

    public CarrierClient(RestClient.Builder builder, @Value("${lab.carrier.base-url}") String baseUrl,
                         MeterRegistry registry) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.retries = Counter.builder("logistpulse.carrier.retries")
                .description("Reintentos al transportista por errores 5xx").register(registry);
    }

    public Optional<Integer> etaMinutes(String orderCode) {
        for (int attempt = 0; ; attempt++) {
            try {
                CarrierEta eta = client.get()
                        .uri("/carrier/eta?code={code}", orderCode)
                        .retrieve()
                        .body(CarrierEta.class);
                if (attempt > 0) {
                    log.info("Transportista respondió para {} tras {} reintento(s)", orderCode, attempt);
                }
                return Optional.ofNullable(eta).map(CarrierEta::etaMinutes);
            } catch (HttpServerErrorException e) {
                if (attempt >= MAX_RETRIES) {
                    log.warn("Transportista no disponible para {}: {} tras {} reintentos", orderCode,
                            e.getStatusCode(), MAX_RETRIES);
                    return Optional.empty();
                }
                long wait = BASE_BACKOFF.toMillis() << attempt;      // 200 ms, 400 ms
                log.info("Transportista {} para {}: reintento {}/{} en {} ms", e.getStatusCode(), orderCode,
                        attempt + 1, MAX_RETRIES, wait);
                retries.increment();
                if (!sleep(wait)) {
                    return Optional.empty();
                }
            } catch (RestClientException e) {
                // 4xx, timeout, conexión rechazada: sin reintento, degradamos de inmediato
                log.warn("Transportista no disponible para {}: {}", orderCode, e.getMessage());
                return Optional.empty();
            }
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
