#!/usr/bin/env bash
# Parte 5.3 + reto 5: tras cambiar el costo de la zona (5 -> 2) y redesplegar, Redis sigue sirviendo rutas viejas.
source "$(dirname "$0")/_lib.sh"
plano() { python "$(dirname "$0")/plano.py" "$HOST" "$@"; }

say "La API ya corre con CONGESTED_COST=2 (ver la leyenda del plano)"
run "curl -s $HOST/api/routes/grid | grep -o '\"legend\":\"[^\"]*\"'"
say "Pero (4,4)->(6,4) devuelve la ruta calculada con costo 5: sale de Redis (clave sin versión del plano)"
run "docker compose exec -T redis redis-cli keys '*'"
plano 4 4 6 4
say "Solo al vaciar la caché aparece la ruta correcta con costo 2 (ahora conviene cruzar)"
run "docker compose exec -T redis redis-cli del 'routes::4:4>6:4'"
plano 4 4 6 4
