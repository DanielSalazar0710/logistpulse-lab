package com.logistpulse.lab.service;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.api.dto.OrderDto;
import com.logistpulse.lab.api.dto.OrderInput;
import com.logistpulse.lab.api.dto.PageDto;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    /** Un AMR con menos batería que esto no recibe pedidos. */
    static final int MIN_BATTERY = 20;

    private final OrderRepository orders;
    private final RobotRepository robots;
    private final WarehouseRepository warehouses;
    private final RoutePlanner planner;
    private final CarrierClient carrier;
    private final DispatchEventPublisher events;
    private final Counter created;
    private final Counter dispatched;
    private final Counter etaFallback;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public OrderService(OrderRepository orders, RobotRepository robots, WarehouseRepository warehouses,
                        RoutePlanner planner, CarrierClient carrier, DispatchEventPublisher events,
                        MeterRegistry registry, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.robots = robots;
        this.warehouses = warehouses;
        this.planner = planner;
        this.carrier = carrier;
        this.events = events;
        this.created = Counter.builder("logistpulse.orders.created").description("Pedidos creados").register(registry);
        this.dispatched = Counter.builder("logistpulse.orders.dispatched").description("Pedidos despachados").register(registry);
        this.etaFallback = Counter.builder("logistpulse.carrier.fallback")
                .description("Despachos que usaron el ETA local porque el transportista no respondió").register(registry);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    @Transactional
    public OrderDto create(OrderInput in) {
        Warehouse warehouse = warehouses.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(500, "No hay almacén configurado"));
        if (!planner.isWalkable(in.destX(), in.destY())) {
            throw new ApiException(422, "Destino (" + in.destX() + "," + in.destY() + ") bloqueado o fuera de la grilla");
        }
        String code = in.code() != null ? in.code() : newCode();
        if (orders.existsByCode(code)) {
            throw new ApiException(409, "Ya existe un pedido con código " + code);
        }
        Order saved = orders.save(new Order(code, in.customer(), in.destX(), in.destY(), warehouse));
        created.increment();
        return OrderDto.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return OrderDto.from(find(id));
    }

    /** Tamaño máximo de página: lo impone el servicio, no solo el recurso (ver Parte 3, reto). */
    public static final int MAX_PAGE_SIZE = 100;

    /** Parte 3.2: lista blanca de campos ordenables → propiedad de la entidad. El cliente nunca ve nombres internos. */
    static final Map<String, String> SORTABLE = Map.of(
            "id", "id",
            "code", "code",
            "customer", "customerName",
            "status", "status",
            "createdAt", "createdAt",
            "eta", "etaMinutes");

    @Transactional(readOnly = true)
    public PageDto<OrderDto> list(OrderStatus status, int page, int size) {
        return list(status, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageDto<OrderDto> list(OrderStatus status, String customer, String sort, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));
        boolean byCustomer = customer != null && !customer.isBlank();
        Page<Order> result;
        if (status != null && byCustomer) {
            result = orders.findByStatusAndCustomerNameContainingIgnoreCase(status, customer.trim(), pageable);
        } else if (status != null) {
            result = orders.findByStatus(status, pageable);
        } else if (byCustomer) {
            result = orders.findByCustomerNameContainingIgnoreCase(customer.trim(), pageable);
        } else {
            result = orders.findAll(pageable);
        }
        List<OrderDto> content = result.getContent().stream().map(OrderDto::from).toList();
        return new PageDto<>(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages(), Map.of());
    }

    /** "createdAt,desc" → Sort; campo fuera de la lista blanca → 400. Desempate por id para que la paginación sea estable. */
    static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("id").descending();
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String property = SORTABLE.get(field);
        if (property == null) {
            throw new ApiException(400, "No se puede ordenar por '" + field + "'. Campos permitidos: "
                    + new TreeSet<>(SORTABLE.keySet()));
        }
        String dir = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new ApiException(400, "Dirección de orden inválida '" + dir + "' (usa asc o desc)");
        }
        Sort primary = dir.equals("desc") ? Sort.by(property).descending() : Sort.by(property).ascending();
        return property.equals("id") ? primary : primary.and(Sort.by("id").descending());
    }

    /** Lo que se necesita saber del pedido antes de salir a la red (sin entidades JPA fuera de la transacción). */
    private record DispatchRequest(String code, Point origin, Point destination) { }

    /**
     * CREATED → DISPATCHED: asigna el AMR libre con batería suficiente, calcula ruta y ETA y publica el evento.
     *
     * Parte 6.3 (refactor de la "trampa deliberada"): antes todo ocurría dentro de una sola @Transactional y la
     * llamada HTTP al transportista (hasta 2 s, más reintentos) retenía una conexión de la pool y la transacción
     * abierta ("idle in transaction" en Postgres). Ahora son tres fases:
     *   1. transacción corta de solo lectura: valida el estado y que haya AMR (falla rápido, sin tocar la red);
     *   2. SIN transacción: ruta A* (caché) y ETA del transportista (red, timeouts, reintentos);
     *   3. transacción corta de escritura: vuelve a validar (el mundo pudo cambiar durante la fase 2), toma el
     *      AMR y guarda. El bloqueo optimista (@Version) sigue protegiendo contra despachos simultáneos.
     */
    public OrderDto dispatch(Long id) {
        DispatchRequest req = readTx.execute(status -> {
            Order order = find(id);
            checkCanDispatch(order);
            pickRobot();
            Warehouse w = order.getWarehouse();
            return new DispatchRequest(order.getCode(), new Point(w.getOriginX(), w.getOriginY()),
                    new Point(order.getDestX(), order.getDestY()));
        });

        RouteDto route = planner.plan(req.origin(), req.destination());
        int eta = carrier.etaMinutes(req.code()).orElseGet(() -> {
            etaFallback.increment();
            return route.cost() * 2;
        });

        return writeTx.execute(status -> {
            Order order = find(id);
            checkCanDispatch(order);
            Robot robot = pickRobot();
            order.setStatus(OrderStatus.DISPATCHED);
            order.setRobot(robot);
            order.setEtaMinutes(eta);
            robot.setStatus(RobotStatus.BUSY);
            dispatched.increment();
            events.publishAfterCommit(order.getId());
            return OrderDto.from(order);
        });
    }

    private static void checkCanDispatch(Order order) {
        if (!order.getStatus().canGoTo(OrderStatus.DISPATCHED)) {
            throw new ApiException(409, "Transición ilegal " + order.getStatus() + " → DISPATCHED");
        }
    }

    private Robot pickRobot() {
        return robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, MIN_BATTERY)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(409, "No hay AMR disponible (libre y con batería ≥ " + MIN_BATTERY + "%)"));
    }

    /** Borrado físico permitido solo mientras el pedido no ha comprometido recursos (estado CREATED). */
    @Transactional
    public void delete(Long id) {
        Order order = find(id);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ApiException(409, "Solo se pueden borrar pedidos CREATED; el pedido " + id
                    + " está " + order.getStatus());
        }
        orders.delete(order);
    }

    /** Transiciones restantes: IN_TRANSIT, DELIVERED, CANCELLED, RETURNED. Valida contra la máquina de estados. */
    @Transactional
    public OrderDto transition(Long id, OrderStatus to) {
        if (to == OrderStatus.DISPATCHED) {
            throw new ApiException(400, "Para despachar usa POST /api/orders/{id}/dispatch");
        }
        Order order = find(id);
        if (!order.getStatus().canGoTo(to)) {
            throw new ApiException(409, "Transición ilegal " + order.getStatus() + " → " + to);
        }
        Robot robot = order.getRobot();
        if (to == OrderStatus.DELIVERED && robot != null) {
            Warehouse w = order.getWarehouse();
            RouteDto route = planner.plan(new Point(w.getOriginX(), w.getOriginY()),
                    new Point(order.getDestX(), order.getDestY()));
            robot.setBattery(robot.getBattery() - route.cost());   // 1% de batería por unidad de costo (ida y vuelta)
            robot.setStatus(RobotStatus.IDLE);
        }
        if (to == OrderStatus.CANCELLED && robot != null) {
            robot.setStatus(RobotStatus.IDLE);
            order.setRobot(null);
        }
        order.setStatus(to);
        return OrderDto.from(order);
    }

    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ApiException(404, "Pedido " + id + " no existe"));
    }

    private static String newCode() {
        return "LP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
