#!/usr/bin/env bash
# Parte 7.1 y 7.2 · Flujo normal y consumidor detenido: los mensajes se acumulan en orders.dispatched.
source "$(dirname "$0")/_lib.sh"
reset_fleet

say "7.1 Flujo normal: outbox -> RabbitMQ -> flota (pedido pasa a IN_TRANSIT)"
ID=$(new_order "Flujo normal"); DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -s -o /dev/null -X POST "$HOST/api/orders/$ID/dispatch"; sleep 3
docker compose logs --no-log-prefix --since "$DESDE" api | grep -E "publicado|Flota recibió" | sed -E 's/^.*(OutboxRelay|FleetListener) *: /\1: /'
run "curl -s $HOST/api/orders/$ID | grep -o '\"status\":\"[A-Z_]*\"'"

say "7.2 Arranco la API SIN consumidor (FLEET_AUTO_STARTUP=false) y despacho 3 pedidos"
FLEET_AUTO_STARTUP=false docker compose up -d api 2>&1 | tail -1; wait_api; reset_fleet
for n in 1 2 3; do id=$(new_order "Sin consumidor $n"); curl -s -o /dev/null -w "despacho $id -> %{http_code}\n" -X POST "$HOST/api/orders/$id/dispatch"; done
sleep 3
run colas
run "curl -s '$HOST/api/orders?status=DISPATCHED' | grep -o '\"totalElements\":[0-9]*'"
