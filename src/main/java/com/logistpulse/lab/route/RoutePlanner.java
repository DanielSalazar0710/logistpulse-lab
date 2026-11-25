package com.logistpulse.lab.route;

import com.logistpulse.lab.api.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Planificador de rutas. El resultado se cachea (Redis en el perfil docker, memoria en local);
 * el Timer solo mide los cálculos reales, es decir, los cache-miss.
 *
 * Reto Parte 5.5 (invalidación): la clave incluye la versión del plano ({@link WarehouseGrid#version()}),
 * una huella de las filas y de los costos. Si cambia el plano o un costo, cambia la clave y las rutas viejas
 * dejan de leerse solas; además el TTL de Redis (10 min) las borra después.
 */
@Service
public class RoutePlanner {

    private final WarehouseGrid grid = WarehouseGrid.defaultLayout();
    private final AStarPathfinder finder = new AStarPathfinder(grid);
    private final AStarPathfinder diagonalFinder = new AStarPathfinder(grid, true);
    private final Timer planTimer;

    public RoutePlanner(MeterRegistry registry) {
        this.planTimer = Timer.builder("logistpulse.route.plan")
                .description("Tiempo de cálculo A* (solo cache-miss)")
                .register(registry);
    }

    public WarehouseGrid grid() {
        return grid;
    }

    /** Usado en la clave de caché (SpEL: #root.target.gridVersion()). */
    public String gridVersion() {
        return grid.version();
    }

    public boolean isWalkable(int x, int y) {
        return grid.walkable(x, y);
    }

    @Cacheable(cacheNames = "routes",
               key = "#root.target.gridVersion() + ':4:' + #from.x() + ':' + #from.y() + '>' + #to.x() + ':' + #to.y()")
    public RouteDto plan(Point from, Point to) {
        return compute(finder, from, to);
    }

    /** Reto Parte 5.4: rutas con 8 vecinos (diagonales con costo √2). */
    @Cacheable(cacheNames = "routes",
               key = "#root.target.gridVersion() + ':8:' + #from.x() + ':' + #from.y() + '>' + #to.x() + ':' + #to.y()")
    public RouteDto planDiagonal(Point from, Point to) {
        return compute(diagonalFinder, from, to);
    }

    private RouteDto compute(AStarPathfinder f, Point from, Point to) {
        return planTimer.record(() -> f.find(from, to))
                .orElseThrow(() -> new ApiException(422, "No existe ruta entre "
                        + from + " y " + to + " (celda bloqueada o fuera de la grilla)"));
    }
}
