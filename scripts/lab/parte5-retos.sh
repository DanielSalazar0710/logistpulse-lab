#!/usr/bin/env bash
# Parte 5 · Retos: 8 vecinos con costo √2 y claves de caché versionadas con la huella del plano.
source "$(dirname "$0")/_lib.sh"
plano() { python "$(dirname "$0")/plano.py" "$HOST" "$@"; }

say "Reto 5.4: misma ruta con 4 y con 8 vecinos (diagonales sin cortar esquinas de estanterías)"
plano 0 0 11 7
plano 0 0 11 7 diagonal

say "Reto 5.5: la clave ahora lleva la versión del plano (huella de filas + costos) y el tipo de vecindad"
run "docker compose exec -T redis redis-cli --scan --pattern 'routes::*' | sort"
