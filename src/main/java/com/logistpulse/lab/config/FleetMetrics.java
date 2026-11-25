package com.logistpulse.lab.config;

import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import java.time.Duration;
import java.time.Instant;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Gauges de la flota, visibles en /actuator/prometheus y en el dashboard de Grafana. */
@Component
public class FleetMetrics implements MeterBinder {

    private final RobotRepository robots;
    private final OrderRepository orders;

    public FleetMetrics(RobotRepository robots, OrderRepository orders) {
        this.robots = robots;
        this.orders = orders;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("logistpulse.robots.idle", robots, r -> r.countByStatus(RobotStatus.IDLE))
                .description("AMR libres").register(registry);
        Gauge.builder("logistpulse.robots.busy", robots, r -> r.countByStatus(RobotStatus.BUSY))
                .description("AMR en misión").register(registry);

        // Reto Parte 8.6: pedidos por estado (una serie por estado: logistpulse_orders_by_status{status="..."})
        for (OrderStatus st : OrderStatus.values()) {
            Gauge.builder("logistpulse.orders.by.status", orders, o -> o.countByStatus(st))
                    .tag("status", st.name())
                    .description("Pedidos en cada estado").register(registry);
        }

        // Parte 8.5: antigüedad del pedido que lleva más tiempo en DISPATCHED (0 si no hay). Si el evento se
        // perdiera o la flota se detuviera, este valor crece sin parar: es la base del tercer SLO.
        Gauge.builder("logistpulse.orders.dispatched.oldest.age", orders, o -> {
                    Instant oldest = o.oldestDispatchedAt();
                    return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toMillis() / 1000.0;
                })
                .baseUnit("seconds")
                .description("Segundos que lleva en DISPATCHED el pedido más antiguo").register(registry);
    }
}
