#!/usr/bin/env bash
# Parte 8.1 y 8.2 · Carga para el dashboard y agotamiento de la flota.
source "$(dirname "$0")/_lib.sh"
reset_fleet

say "8.1 Carga: 40 pedidos y 60 rutas aleatorias"
for i in $(seq 1 40); do curl -s -o /dev/null -X POST -H "$J" -d '{"customer":"carga","destX":10,"destY":5}' "$HOST/api/orders"; done
for i in $(seq 1 60); do curl -s -o /dev/null "$HOST/api/routes?toX=$((RANDOM % 12))&toY=$((RANDOM % 8))"; done
echo "pedidos CREATED: $(curl -s "$HOST/api/orders?status=CREATED&size=1" | grep -o '"totalElements":[0-9]*' | cut -d: -f2)"

say "8.2 Despacho pedidos hasta agotar la flota"
for id in $(curl -s "$HOST/api/orders?customer=carga&status=CREATED&size=8&sort=id,asc" | grep -o '"id":[0-9]*' | cut -d: -f2); do
  printf 'pedido %s -> ' "$id"; curl -s -w ' <- %{http_code}\n' -X POST "$HOST/api/orders/$id/dispatch" | sed -E 's/.*"robot":"([^"]*)".*(<- [0-9]+)/robot \1 \2/'
done
run "curl -s $HOST/actuator/prometheus | grep -E '^logistpulse_robots_(idle|busy)'"
