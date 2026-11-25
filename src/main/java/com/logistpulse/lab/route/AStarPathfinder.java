package com.logistpulse.lab.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A* sobre la grilla del almacén con costos por celda.
 * <ul>
 *   <li>4 vecinos (por defecto): heurística Manhattan, admisible porque el costo mínimo de una celda es 1.</li>
 *   <li>8 vecinos (reto Parte 5.4): las diagonales cuestan √2 × costo de la celda y la heurística es la distancia
 *       octil, también admisible con costo mínimo 1. No se permite "cortar esquinas" entre dos estanterías.</li>
 * </ul>
 */
public class AStarPathfinder {

    private static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIRS8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double SQRT2 = Math.sqrt(2);

    private final WarehouseGrid grid;
    private final boolean diagonal;

    public AStarPathfinder(WarehouseGrid grid) {
        this(grid, false);
    }

    public AStarPathfinder(WarehouseGrid grid, boolean diagonal) {
        this.grid = grid;
        this.diagonal = diagonal;
    }

    public Optional<RouteDto> find(Point start, Point goal) {
        if (!grid.walkable(start) || !grid.walkable(goal)) {
            return Optional.empty();
        }
        int w = grid.width();
        int size = w * grid.height();
        double[] g = new double[size];
        int[] prev = new int[size];
        boolean[] closed = new boolean[size];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);

        int s = start.y() * w + start.x();
        int t = goal.y() * w + goal.x();
        g[s] = 0;

        // elementos: {índice de celda, f = g + h}
        PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        open.add(new double[]{s, heuristic(start.x(), start.y(), goal)});

        while (!open.isEmpty()) {
            int cur = (int) open.poll()[0];
            if (closed[cur]) {
                continue;
            }
            closed[cur] = true;
            if (cur == t) {
                return Optional.of(rebuild(prev, t, g[t], w));
            }
            int cx = cur % w;
            int cy = cur / w;
            for (int[] d : diagonal ? DIRS8 : DIRS4) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (!grid.walkable(nx, ny)) {
                    continue;
                }
                boolean isDiagonal = d[0] != 0 && d[1] != 0;
                // sin cortar esquinas: para moverse en diagonal las dos celdas ortogonales deben estar libres
                if (isDiagonal && (!grid.walkable(cx + d[0], cy) || !grid.walkable(cx, cy + d[1]))) {
                    continue;
                }
                int ni = ny * w + nx;
                if (closed[ni]) {
                    continue;
                }
                double step = grid.cost(nx, ny) * (isDiagonal ? SQRT2 : 1);
                double ng = g[cur] + step;
                if (ng < g[ni]) {
                    g[ni] = ng;
                    prev[ni] = cur;
                    open.add(new double[]{ni, ng + heuristic(nx, ny, goal)});
                }
            }
        }
        return Optional.empty();
    }

    /** Estimación del costo restante. Nunca sobreestima mientras el costo mínimo de una celda sea 1. */
    double heuristic(int x, int y, Point goal) {
        int dx = Math.abs(x - goal.x());
        int dy = Math.abs(y - goal.y());
        if (!diagonal) {
            return dx + dy;                                   // Manhattan
        }
        return (dx + dy) + (SQRT2 - 2) * Math.min(dx, dy);    // octil
    }

    private static RouteDto rebuild(int[] prev, int target, double cost, int w) {
        List<Point> path = new ArrayList<>();
        for (int i = target; prev[i] != -1; i = prev[i]) {
            path.add(new Point(i % w, i / w));
        }
        Collections.reverse(path);
        // RouteDto.cost es entero (batería y ETA lo usan así); en 4 vecinos el valor es exacto.
        return new RouteDto(path, (int) Math.round(cost), path.size());
    }
}
