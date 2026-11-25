package com.logistpulse.lab.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AStarPathfinderTest {

    private final WarehouseGrid grid = WarehouseGrid.defaultLayout();
    private final AStarPathfinder finder = new AStarPathfinder(grid);

    @Test
    void straightCorridorCostsOnePerStep() {
        RouteDto r = finder.find(new Point(0, 0), new Point(11, 0)).orElseThrow();
        assertThat(r.steps()).isEqualTo(11);
        assertThat(r.cost()).isEqualTo(11);
    }

    @Test
    void blockedDestinationHasNoRoute() {
        assertThat(finder.find(new Point(0, 0), new Point(1, 1))).isEmpty();
    }

    @Test
    void outOfBoundsHasNoRoute() {
        assertThat(finder.find(new Point(0, 0), new Point(99, 99))).isEmpty();
    }

    @Test
    void pathNeverCrossesShelves() {
        RouteDto r = finder.find(new Point(0, 0), new Point(11, 7)).orElseThrow();
        assertThat(r.path()).allMatch(grid::walkable);
        assertThat(r.path().get(r.path().size() - 1)).isEqualTo(new Point(11, 7));
    }

    @Test
    void avoidsCongestedZoneWhenDetourIsCheaper() {
        // Directo por la zona congestionada costaría 2+2+2+1 = 7 (con costo 5 eran 16); rodeando por la fila 2 cuesta 6.
        Optional<RouteDto> r = finder.find(new Point(3, 3), new Point(7, 3));
        assertThat(r).isPresent();
        assertThat(r.get().cost()).isEqualTo(6);
        assertThat(r.get().path()).noneMatch(p -> grid.rows().get(p.y()).charAt(p.x()) == ',');
    }

    @Test
    void withCost2CrossingTheZoneBecomesCheaper() {
        // Parte 5.3: de (4,4) a (6,4) cruzar cuesta 2+2 = 4; rodear por la fila 5 cuesta 1+1+1+2 = 5.
        // Con el costo original (5) era al revés: cruzar 10 y rodear 8.
        RouteDto r = finder.find(new Point(4, 4), new Point(6, 4)).orElseThrow();
        assertThat(r.cost()).isEqualTo(4);
        assertThat(r.path()).containsExactly(new Point(5, 4), new Point(6, 4));
    }

    @Test
    void congestedCellCostsTheConfiguredValue() {
        assertThat(grid.cost(5, 3)).isEqualTo(WarehouseGrid.CONGESTED_COST).isEqualTo(2);
        assertThat(grid.cost(0, 0)).isEqualTo(1);
    }

    @Test
    void consecutiveCellsAreAdjacent() {
        RouteDto r = finder.find(new Point(0, 0), new Point(10, 5)).orElseThrow();
        Point prev = new Point(0, 0);
        for (Point p : r.path()) {
            assertThat(Math.abs(p.x() - prev.x()) + Math.abs(p.y() - prev.y())).isEqualTo(1);
            prev = p;
        }
    }
}
