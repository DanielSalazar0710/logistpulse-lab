#!/usr/bin/env bash
# Parte 5 · Rutas A* (costos ponderados) y caché de rutas en Redis.
source "$(dirname "$0")/_lib.sh"
plano() { python "$(dirname "$0")/plano.py" "$HOST" "$@"; }
misses() { curl -s "$HOST/actuator/prometheus" | grep '^logistpulse_route_plan_seconds_count' | awk '{print $2}'; }

say "5.1 Leyenda: . libre (1)   , congestionado (${COSTO:-5})   # estantería"
say "Dos rutas de lado a lado de la zona congestionada"
plano 3 3 7 3
plano 4 4 6 4
say "Destino DENTRO de la zona: entra por el borde más barato"
plano 5 0 5 4

say "5.2 Caché: misma ruta dos veces; el Timer solo cuenta los cálculos reales (cache-miss)"
docker compose exec -T redis redis-cli flushall >/dev/null
echo "cálculos A* antes          : $(misses)"
curl -s -o /dev/null "$HOST/api/routes?fromX=0&fromY=0&toX=10&toY=5"; echo "tras 1.a petición (miss)   : $(misses)"
curl -s -o /dev/null "$HOST/api/routes?fromX=0&fromY=0&toX=10&toY=5"; echo "tras 2.a petición (hit)    : $(misses)"
run "docker compose exec -T redis redis-cli keys '*'"
run "docker compose exec -T redis redis-cli ttl \"\$(docker compose exec -T redis redis-cli keys '*' | head -1 | tr -d '\r')\""
