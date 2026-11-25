#!/usr/bin/env bash
# Parte 7 · Fallos del publicador "afterCommit" y del consumidor NO idempotente (antes del Outbox).
source "$(dirname "$0")/_lib.sh"
RMQ="curl -s -u guest:guest"
reset_fleet

say "7.3 RabbitMQ caído: el commit ya ocurrió, pero el envío afterCommit falla"
docker compose stop rabbitmq 2>&1 | tail -1
ID=$(new_order "Sin broker")
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
run "curl -s -o /dev/null -w 'POST /api/orders/$ID/dispatch -> HTTP %{http_code}\n' -X POST $HOST/api/orders/$ID/dispatch"
run "curl -s $HOST/api/orders/$ID | grep -o '\"status\":\"[A-Z_]*\"\|\"robot\":\"[^\"]*\"'"
sleep 1; docker compose logs --no-log-prefix --since "$DESDE" api | grep -m2 -E 'AmqpConnectException|Connection refused|UnknownHost|ERROR' | cut -c1-180
docker compose start rabbitmq 2>&1 | tail -1
echo "esperando a RabbitMQ..."; for i in $(seq 1 30); do $RMQ http://localhost:15672/api/overview >/dev/null && break; sleep 2; done; sleep 8
run "curl -s $HOST/api/orders/$ID | grep -o '\"status\":\"[A-Z_]*\"'"
echo "-> el cliente recibió 500, pero el pedido quedó DISPATCHED con su AMR ocupado y el evento se perdió: nunca pasará a IN_TRANSIT."

say "7.5 Mensaje duplicado: el mismo order.dispatched llega dos veces"
reset_fleet
ID2=$(new_order "Duplicado")
curl -s -o /dev/null -X POST "$HOST/api/orders/$ID2/dispatch"; sleep 2
run "curl -s $HOST/api/orders/$ID2 | grep -o '\"status\":\"[A-Z_]*\"'"
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
run "$RMQ -H 'content-type: application/json' -X POST -d '{\"properties\":{},\"routing_key\":\"orders.dispatched\",\"payload\":\"$ID2\",\"payload_encoding\":\"string\"}' http://localhost:15672/api/exchanges/%2F/amq.default/publish"
sleep 5
N=$(docker compose logs --no-log-prefix --since "$DESDE" api | grep -c "Flota recibió order.dispatched id=$ID2")
echo "En 5 s el consumidor recibió el duplicado $N veces (el mensaje vuelve a la cola tras cada excepción):"
docker compose logs --no-log-prefix --since "$DESDE" api | grep -E "Flota recibió|Transición ilegal|ListenerExecutionFailed" | sed -E 's/^.{0,70}(INFO|WARN|ERROR)/\1/' | cut -c1-170 | head -6
