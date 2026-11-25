package com.logistpulse.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.api.dto.OrderDto;
import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.domain.Warehouse;
import com.logistpulse.lab.integration.CarrierClient;
import com.logistpulse.lab.messaging.DispatchEventPublisher;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import com.logistpulse.lab.repo.WarehouseRepository;
import com.logistpulse.lab.route.Point;
import com.logistpulse.lab.route.RouteDto;
import com.logistpulse.lab.route.RoutePlanner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Parte 9.2 · Pruebas unitarias de OrderService.dispatch con Mockito (sin Spring, sin base de datos).
 * El PlatformTransactionManager es un mock: TransactionTemplate ejecuta el callback igual, sin transacción real.
 */
class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final RobotRepository robots = mock(RobotRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final RoutePlanner planner = mock(RoutePlanner.class);
    private final CarrierClient carrier = mock(CarrierClient.class);
    private final DispatchEventPublisher events = mock(DispatchEventPublisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private OrderService service;
    private Warehouse warehouse;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, robots, warehouses, planner, carrier, events, registry,
                mock(PlatformTransactionManager.class));
        warehouse = new Warehouse("CD Prueba", 0, 0);
        order = new Order("LP-TEST01", "Cliente prueba", 10, 5, warehouse);
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(planner.plan(any(Point.class), any(Point.class)))
                .thenReturn(new RouteDto(List.of(new Point(10, 5)), 15, 15));
    }

    @Test
    void withoutRobotsDispatchFailsWith409AndDoesNotCallTheCarrier() {
        when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.dispatch(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No hay AMR disponible")
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(409);

        verify(carrier, never()).etaMinutes(anyString());   // falla rápido, antes de salir a la red
        verify(events, never()).publish(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void alreadyDispatchedOrderFailsWith409() {
        order.setStatus(OrderStatus.DISPATCHED);

        assertThatThrownBy(() -> service.dispatch(1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Transición ilegal DISPATCHED → DISPATCHED")
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(409);

        verify(robots, never()).findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(any(), anyInt());
        verify(events, never()).publish(any());
    }

    @Test
    void whenCarrierIsDownEtaIsRouteCostTimesTwo() {
        Robot robot = new Robot("AMR-01", warehouse, 100);
        when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                .thenReturn(List.of(robot));
        when(carrier.etaMinutes("LP-TEST01")).thenReturn(Optional.empty());

        OrderDto dto = service.dispatch(1L);

        assertThat(dto.etaMinutes()).isEqualTo(15 * 2);
        assertThat(dto.status()).isEqualTo(OrderStatus.DISPATCHED);
        assertThat(dto.robot()).isEqualTo("AMR-01");
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.BUSY);
        assertThat(registry.get("logistpulse.carrier.fallback").counter().count()).isEqualTo(1.0);
        verify(events).publish(order.getId());
    }

    @Test
    void whenCarrierAnswersItsEtaIsUsed() {
        when(robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, OrderService.MIN_BATTERY))
                .thenReturn(List.of(new Robot("AMR-02", warehouse, 90)));
        when(carrier.etaMinutes("LP-TEST01")).thenReturn(Optional.of(25));

        assertThat(service.dispatch(1L).etaMinutes()).isEqualTo(25);
        assertThat(registry.get("logistpulse.carrier.fallback").counter().count()).isZero();
    }

    @Test
    void duplicateTransitEventIsIgnoredWithoutException() {
        order.setStatus(OrderStatus.IN_TRANSIT);
        assertThat(service.startTransitIfDispatched(1L)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);

        order.setStatus(OrderStatus.DISPATCHED);
        assertThat(service.startTransitIfDispatched(1L)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    @Test
    void sortOutsideTheWhitelistIsRejected() {
        assertThatThrownBy(() -> OrderService.parseSort("version,desc"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(400);
        assertThat(OrderService.parseSort("createdAt,desc").getOrderFor("createdAt").isDescending()).isTrue();
    }
}
