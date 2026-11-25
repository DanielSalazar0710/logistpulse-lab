#!/usr/bin/env bash
# Parte 7.2 (reanudar), 7.4 Outbox con RabbitMQ caído y 7.5 consumidor idempotente + DLQ.
source "$(dirname "$0")/_lib.sh"
PSQL="docker compose exec -T db psql -U logistpulse -X -q"
RMQ="curl -s -u guest:guest"

say "7.2 Reactivo el consumidor: procesa los mensajes acumulados"
docker compose up -d api 2>&1 | tail -1; wait_api; sleep 4
run colas
run "curl -s '$HOST/api/orders?status=DISPATCHED' | grep -o '\"totalElements\":[0-9]*'"

say "7.4 Outbox con RabbitMQ caído: el despacho responde 200 y el evento espera en la base"
reset_fleet
docker compose stop rabbitmq 2>&1 | tail -1
ID=$(new_order "Outbox sin broker")
run "curl -s -o /dev/null -w 'POST /api/orders/$ID/dispatch -> HTTP %{http_code}\n' -X POST $HOST/api/orders/$ID/dispatch"
sleep 3
run "$PSQL -c 'select id, event_type, aggregate_id, sent_at is not null as enviado, attempts from outbox_event order by id desc limit 1'"
run "curl -s $HOST/actuator/prometheus | grep '^logistpulse_outbox_pending'"
docker compose start rabbitmq 2>&1 | tail -1
echo "esperando a RabbitMQ y al relay..."; for i in $(seq 1 40); do [ "$($PSQL -tAc "select count(*) from outbox_event where sent_at is null")" = 0 ] && break; sleep 2; done
run "$PSQL -c 'select id, event_type, aggregate_id, sent_at is not null as enviado, attempts from outbox_event order by id desc limit 1'"
sleep 2; run "curl -s $HOST/api/orders/$ID | grep -o '\"status\":\"[A-Z_]*\"'"

say "7.5 Duplicado: publico otra vez el evento del pedido $ID (ya IN_TRANSIT)"
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
$RMQ -H 'content-type: application/json' -X POST -d "{\"properties\":{\"message_id\":\"manual-dup\"},\"routing_key\":\"orders.dispatched\",\"payload\":\"$ID\",\"payload_encoding\":\"string\"}" http://localhost:15672/api/exchanges/%2F/amq.default/publish; echo
sleep 4
echo "Entregas del duplicado en 4 s: $(docker compose logs --no-log-prefix --since "$DESDE" api | grep -c "manual-dup")"
docker compose logs --no-log-prefix --since "$DESDE" api | grep -E "manual-dup|ignorado" | sed -E 's/^.*FleetListener *: /FleetListener: /'

say "7.5 Mensaje venenoso (payload no numérico): 1 intento + 3 reintentos (500 ms) y a la DLQ, sin bucle"
DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
$RMQ -H 'content-type: application/json' -X POST -d '{"properties":{"message_id":"veneno"},"routing_key":"orders.dispatched","payload":"abc","payload_encoding":"string"}' http://localhost:15672/api/exchanges/%2F/amq.default/publish; echo
sleep 4
echo "Intentos de procesar el mensaje venenoso: $(docker compose logs --no-log-prefix --since "$DESDE" api | grep -c "Flota recibió order.dispatched id=abc")"
run colas
run "curl -s $HOST/actuator/prometheus | grep '^logistpulse_fleet_duplicates'"
