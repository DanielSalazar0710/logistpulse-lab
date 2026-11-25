package com.logistpulse.lab.repo;

import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // El EntityGraph evita el problema N+1 al mapear warehouse y robot en el DTO.
    // Ejercicio: quítalo, activa spring.jpa.show-sql=true y cuenta las consultas.
    @Override
    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // Parte 3.1: filtro ?customer= (contiene, sin distinguir mayúsculas) con métodos derivados.
    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByCustomerNameContainingIgnoreCase(String customer, Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "robot"})
    Page<Order> findByStatusAndCustomerNameContainingIgnoreCase(OrderStatus status, String customer, Pageable pageable);

    boolean existsByCode(String code);

    long countByStatus(OrderStatus status);

    /** Parte 8.5: despacho más antiguo que sigue en DISPATCHED (null si no hay ninguno). */
    @Query("select min(o.dispatchedAt) from Order o where o.status = com.logistpulse.lab.domain.OrderStatus.DISPATCHED")
    java.time.Instant oldestDispatchedAt();
}
